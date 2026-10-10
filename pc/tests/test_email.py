from email.message import EmailMessage

import pytest

from fintrack_pc.email_import import EmailError, import_email, parse_email, receipt_total


def make(sender, subject, body, html=False, date="Sat, 10 Oct 2026 13:05:00 +0530", mid="<m1@x>"):
    msg = EmailMessage()
    msg["From"] = sender
    msg["Subject"] = subject
    msg["Date"] = date
    msg["Message-ID"] = mid
    if html:
        msg.set_content("plain fallback")
        msg.add_alternative(body, subtype="html")
    else:
        msg.set_content(body)
    return msg


UBER = """<html><head><style>.x{color:red}</style></head><body>
<h1>Thanks for riding, Deepak</h1><p>Here's your receipt for your ride on 10 October.</p>
<table><tr><td>Trip fare</td><td>₹230.10</td></tr><tr><td>Booking fee</td><td>₹15.22</td></tr>
<tr><td><b>Total</b></td><td><b>₹245.32</b></td></tr></table><p>Paid using UPI</p></body></html>"""


def test_uber_html_receipt():
    r = parse_email(make("Uber Receipts <noreply@uber.com>", "Your Saturday afternoon trip with Uber", UBER, html=True))
    assert r["amount_paise"] == 24532 and r["direction"] == "DEBIT"
    assert r["sender_key"] == "UBER" and r["source_type"] == "EMAIL"
    assert r["txn_date"] == "2026-10-10"
    assert "color:red" not in r["body"]


def test_rapido_and_swiggy_receipts():
    rapido = make("Rapido <shoutout@rapido.bike>", "Ride Invoice", "Your ride from Koramangala to HSR\nRide Fare ₹ 98.00\nTotal Amount ₹ 103.00\nPaid by Cash")
    assert parse_email(rapido)["amount_paise"] == 10300
    swiggy = make("Swiggy <noreply@swiggy.in>", "Your Swiggy order summary",
                  "Order from Meghana Foods\nItem Total ₹300.00\nDelivery Fee ₹30.00\nTotal savings ₹50.00\nGrand Total ₹349.00\nPaid via UPI")
    assert parse_email(swiggy)["amount_paise"] == 34900


def test_amount_paid_beats_order_total():
    assert receipt_total("Order Total: ₹1,299.00\nAmazon Pay balance used ₹200\nAmount Paid ₹1,099.00") == 109900


def test_refund_is_credit_and_promos_cancellations_skipped():
    refund = parse_email(make("Zomato <noreply@zomato.com>", "Refund processed", "Refund of ₹220.50 for your order has been processed"))
    assert refund["direction"] == "CREDIT" and refund["amount_paise"] == 22050
    assert parse_email(make("Zomato <promo@zomato.com>", "50% OFF tonight!", "Get flat ₹100 off on orders above ₹199")) is None
    assert parse_email(make("Swiggy <noreply@swiggy.in>", "Your order was cancelled", "Order Total ₹349")) is None
    assert parse_email(make("Friend <a@gmail.com>", "Dinner", "Total ₹500 paid")) is None


def test_bank_alert_email_masked():
    r = parse_email(make("HDFC Bank InstaAlerts <alerts@hdfcbank.net>", "❗ You have done a UPI txn. Check details!",
                         "Dear Customer, Rs.40.00 has been debited from account 1234 to VPA paytmqr281005050101@paytm "
                         "RAMESH TEA STALL on 10-10-26. Your UPI transaction reference number is 628312345678."))
    assert r["amount_paise"] == 4000 and r["direction"] == "DEBIT" and r["sender_key"] == "HDFCBK"
    assert "628312345678" not in r["body"] and "account 1234" not in r["body"]
    assert parse_email(make("HDFC Bank <alerts@hdfcbank.net>", "OTP", "Your OTP for txn of Rs 500 is 123456")) is None


class FakeIMAP:
    def __init__(self, messages):
        self.messages = {str(i + 1).encode(): m for i, m in enumerate(messages)}
        self.fetches = 0

    def __call__(self, host):
        return self

    def login(self, user, pwd):
        if pwd != "app-pass":
            import imaplib
            raise imaplib.IMAP4.error("AUTHENTICATIONFAILED")

    def select(self, box, readonly=False):
        assert readonly
        return "OK", [b"1"]

    def uid(self, cmd, *args):
        if cmd == "search":
            word = args[-1].strip('"')
            hits = [k for k, m in self.messages.items() if word in str(m["From"]).lower()]
            return "OK", [b" ".join(hits)]
        uid, what = args
        msg = self.messages[uid]
        if "HEADER" in what:
            return "OK", [(b"h", f"Message-ID: {msg['Message-ID']}\r\nFrom: {msg['From']}\r\n\r\n".encode())]
        self.fetches += 1
        return "OK", [(b"b", msg.as_bytes())]

    def logout(self):
        pass


def test_import_is_incremental(app):
    imap = FakeIMAP([
        make("Uber Receipts <noreply@uber.com>", "Your trip", UBER, html=True, mid="<u1>"),
        make("Zomato <promo@zomato.com>", "50% OFF", "flat ₹100 off", mid="<p1>"),
    ])
    r = import_email(app.store, "me@gmail.com", "app-pass", 30, imap_factory=imap)
    assert r["emails_checked"] == 2 and r["transactions_added"] == 1
    r = import_email(app.store, "me@gmail.com", "app-pass", 30, imap_factory=imap)
    assert r["emails_checked"] == 0 and imap.fetches == 2  # already-seen emails are not downloaded again


def test_bad_password_message(app):
    with pytest.raises(EmailError, match="App Password"):
        import_email(app.store, "me@gmail.com", "wrong", 30, imap_factory=FakeIMAP([]))
