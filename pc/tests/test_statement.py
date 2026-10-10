from datetime import datetime

import pytest

from fintrack_pc.statement_import import StatementError, import_statement, parse_statement, read_table

HDFC_CSV = """HDFC BANK Ltd.,,,,,,
Statement of account,,,,,,
Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
09/10/26,UPI-SWIGGY-SWIGGY.STORES@AXB-UTIB0000100-628312345678-PAYMENT,0000628312345678,09/10/26,349.00,,"12,000.00"
10/10/26,UPI-RAMESH TEA STALL-PAYTMQR281005@PAYTM-628399999999-UPI,0000628399999999,10/10/26,40.00,,"11,960.00"
10/10/26,UPI-RAMESH TEA STALL-PAYTMQR281005@PAYTM-628399999998-UPI,0000628399999998,10/10/26,40.00,,"11,920.00"
01/10/26,NEFT CR-ACME CORP SALARY,,01/10/26,,"85,000.00","96,920.00"
"""


def test_hdfc_csv(tmp_path):
    f = tmp_path / "hdfc.csv"
    f.write_text(HDFC_CSV)
    rows = parse_statement(read_table(f), "hdfc")
    assert [(r["txn_date"], r["amount_paise"], r["direction"]) for r in rows] == [
        ("2026-10-09", 34900, "DEBIT"), ("2026-10-10", 4000, "DEBIT"), ("2026-10-10", 4000, "DEBIT"),
        ("2026-10-01", 8500000, "CREDIT")]
    assert rows[0]["sender_key"] == "HDFC" and rows[0]["source_type"] == "STATEMENT"
    assert "628312345678" not in rows[0]["body"] and "SWIGGY" in rows[0]["body"]
    assert len({r["uid"] for r in rows}) == 4


def test_sbi_xlsx_amount_and_type(tmp_path):
    openpyxl = pytest.importorskip("openpyxl")
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.append(["Account Name", "DEEPAK"])
    ws.append(["Txn Date", "Value Date", "Description", "Ref No./Cheque No.", "Debit", "Credit", "Balance"])
    ws.append([datetime(2026, 10, 9), datetime(2026, 10, 9), "TO TRANSFER-UPI/DR/628312345678/RAPIDO", "", 103, None, 5000])
    ws.append(["10 Oct 2026", "10 Oct 2026", "BY TRANSFER-UPI/CR/628300000000/ANITA", "", None, "300.00", 5300])
    f = tmp_path / "sbi.xlsx"
    wb.save(f)
    rows = parse_statement(read_table(f), "SBI")
    assert [(r["txn_date"], r["amount_paise"], r["direction"]) for r in rows] == [
        ("2026-10-09", 10300, "DEBIT"), ("2026-10-10", 30000, "CREDIT")]


def test_amount_with_drcr_column_and_wrapped_pdf_lines():
    table = [
        ["Tran Date", "Particulars", "Amount", "Dr/Cr", "Balance"],
        ["09-10-2026", "UPI/P2M/628312345678/UBER INDIA", "245.32", "DR", "1000"],
        [None, "SYSTEMS PVT LTD", None, None, None],  # PDF wrap
        ["Tran Date", "Particulars", "Amount", "Dr/Cr", "Balance"],  # header repeated on page 2
        ["10-10-2026", "REFUND ZOMATO", "220.50", "CR", "1220.50"],
    ]
    rows = parse_statement(table, "AXIS")
    assert rows[0]["amount_paise"] == 24532 and rows[0]["direction"] == "DEBIT"
    assert "UBER INDIA SYSTEMS PVT LTD" in rows[0]["body"]
    assert rows[1]["direction"] == "CREDIT"


def test_reimport_adds_nothing(app, tmp_path):
    f = tmp_path / "hdfc.csv"
    f.write_text(HDFC_CSV)
    first = import_statement(app.store, f, "HDFC")
    assert first["new_lines"] == 4 and first["missed_before"] == 4
    again = import_statement(app.store, f, "HDFC")
    assert again["new_lines"] == 0


def test_no_header_is_a_clear_error(app, tmp_path):
    f = tmp_path / "x.csv"
    f.write_text("a,b\n1,2\n")
    with pytest.raises(StatementError, match="header row"):
        import_statement(app.store, f, "HDFC")
