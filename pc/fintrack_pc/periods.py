"""Spending periods, identical to the phone's (android/.../core/Cycle.kt).

A period starts on a fixed day each month: the last day of the month (default, for a salary credited at month end:
31 Oct – 29 Nov), the 1st (calendar months) or a day from 2 to 28. A period is named after the month it ends in.
The phone's setting is copied to the PC on every sync.
"""

from __future__ import annotations

import calendar
from dataclasses import dataclass
from datetime import date, timedelta

LAST_DAY = 0
CALENDAR = 1


@dataclass(frozen=True)
class Period:
    start: date
    end: date

    @property
    def label_month(self) -> str:
        return f"{self.end.year:04d}-{self.end.month:02d}"

    @property
    def title(self) -> str:
        if self.start.day == 1 and self.end.day == calendar.monthrange(self.end.year, self.end.month)[1] \
                and self.start.month == self.end.month:
            return self.start.strftime("%B %Y")
        return f"{self.start.day} {self.start:%b} – {self.end.day} {self.end:%b} {self.end.year}"

    def as_dict(self) -> dict:
        return {"start": self.start.isoformat(), "end": self.end.isoformat(), "title": self.title,
                "month": self.label_month}


def _add_months(y: int, m: int, n: int) -> tuple[int, int]:
    idx = y * 12 + (m - 1) + n
    return idx // 12, idx % 12 + 1


def start_of(y: int, m: int, start_day: int) -> date:
    last = calendar.monthrange(y, m)[1]
    return date(y, m, last if start_day == LAST_DAY else min(start_day, last))


def containing(d: date, start_day: int) -> Period:
    y, m = d.year, d.month
    if d < start_of(y, m, start_day):
        y, m = _add_months(y, m, -1)
    ny, nm = _add_months(y, m, 1)
    return Period(start_of(y, m, start_day), start_of(ny, nm, start_day) - timedelta(days=1))


def for_month(month: str, start_day: int) -> Period:
    """The period that ends in [month] ("YYYY-MM")."""
    y, m = (int(x) for x in month.split("-"))
    return containing(date(y, m, 1), start_day)


def describe(start_day: int) -> str:
    return {LAST_DAY: "last day of the month", CALENDAR: "1st (calendar month)"}.get(start_day, f"day {start_day}")
