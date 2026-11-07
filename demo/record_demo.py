"""Records the sprint demo videos by driving the running app in a browser, with on-screen captions.

Needs: pip install playwright imageio-ffmpeg && python -m playwright install chromium
Run the app with the dev profile (API on 8080, web on 5173), then:
    python demo/record_demo.py sprint1
    python demo/record_demo.py sprint2 --survey-link <link from the dev log>
The video is written to demo/<sprint>.mp4.
"""
import argparse
import datetime
import os
import re
import shutil
import subprocess
import tempfile

import imageio_ffmpeg
from playwright.sync_api import sync_playwright

WEB = "http://localhost:5173"
HERE = os.path.dirname(os.path.abspath(__file__))

CAPTION_JS = """(text) => {
  let bar = document.getElementById('demo-caption');
  if (!bar) {
    bar = document.createElement('div');
    bar.id = 'demo-caption';
    Object.assign(bar.style, {position: 'fixed', left: '0', right: '0', bottom: '0', zIndex: 99999,
      background: 'rgba(17, 24, 39, 0.92)', color: '#fff', font: '600 20px system-ui, sans-serif',
      padding: '14px 24px', textAlign: 'center'});
    document.body.appendChild(bar);
  }
  bar.textContent = text;
}"""


class Demo:
    def __init__(self, page):
        self.page = page

    def say(self, text, seconds=0.0):
        self.page.evaluate(CAPTION_JS, text)
        self.wait(seconds)

    def wait(self, seconds):
        if seconds:
            self.page.wait_for_timeout(int(seconds * 1000))

    def go(self, path):
        self.page.goto(WEB + path)
        self.page.wait_for_load_state("networkidle")

    def login(self, email):
        self.go("/login")
        self.say(f"Sign in as {email} (password + one-time code for HR roles)")
        self.page.fill("input[type=email]", email)
        self.page.fill("input[type=password]", "Demo@1234")
        self.wait(0.8)
        self.page.click("button:has-text('Sign in')")
        self.page.wait_for_timeout(1200)
        text = self.page.inner_text("body")
        code = re.search(r"current code:\s*(\d{6})", text)
        if code:
            self.say("Second step: the 6-digit authenticator code (shown on screen in demo mode)")
            self.page.get_by_label("One-time code").fill(code.group(1))
            self.wait(1)
            self.page.click("button:has-text('Verify')")
        self.page.wait_for_url(WEB + "/")
        self.page.wait_for_load_state("networkidle")

    def scroll(self, pixels, seconds):
        self.page.mouse.wheel(0, pixels)
        self.wait(seconds)


def sprint1(d):
    d.page.goto(WEB + "/login")
    d.say("Attrition Analysis System - Sprint 1: the working core", 3)
    d.login("hr@acme.test")
    d.say("Attrition dashboard: overall, voluntary and regretted attrition for the year", 7)
    d.scroll(500, 0)
    d.say("Breakdowns by department, tenure band and location", 7)
    d.page.select_option("select[aria-label=Department]", label="Sales")
    d.say("Filter to one department; Department Managers only ever see their own", 5)
    d.go("/employees")
    d.say("Employee records: search, filter, create, edit, CSV import", 2)
    d.page.fill("input[aria-label=Search]", "Sharma")
    d.wait(5)
    d.go("/separations")
    d.say("Separations recorded this year, with tenure, reason and status", 5)
    d.page.click("button:has-text('Record separation')")
    d.say("Record a resignation: pick the employee...", 0)
    d.page.fill("input[placeholder='Type a name or employee ID']", "Iyer")
    d.page.wait_for_timeout(1200)
    d.page.locator(".suggestions button").first.click()
    d.say("...set the dates, type and reason, then save", 0)
    last_day = datetime.date.today() + datetime.timedelta(days=30)
    d.page.locator(".modal input[type=date]").nth(1).fill(last_day.isoformat())
    d.page.locator(".modal select").nth(1).select_option(label="Compensation")
    d.page.check(".modal input[type=checkbox]")
    d.wait(3)
    d.page.click(".modal button:has-text('Save')")
    d.page.wait_for_selector("p.success", timeout=5000)  # fail loudly rather than film a save that did not happen
    d.say("Saved: the employee is now on notice; invalid dates are rejected with a clear message", 6)
    d.go("/")
    d.say("Sprint 1 done: login with OTP, roles, employees, separations, attrition KPIs", 4)


def sprint2(d, survey_link):
    d.page.goto(WEB + "/login")
    d.say("Attrition Analysis System - Sprint 2: surveys, analytics, risk and reports", 3)
    d.login("hr@acme.test")
    d.say("Threshold alerts: departments whose 3-month attrition is above the limit set in Settings", 6)
    d.say("New KPI: early attrition - people who left within their first year", 5)
    d.scroll(450, 0)
    d.say("12-month attrition trend compared with the previous year", 7)
    d.scroll(450, 0)
    d.say("Exit reasons ranked by count, and average exit-survey scores", 7)
    d.page.mouse.wheel(0, -2000)
    d.say("Export: CSV of the figures, or the page as PDF", 3)
    with d.page.expect_download():
        d.page.click("button:has-text('Export CSV')")
    d.wait(1)
    d.go("/separations")
    d.say("Separations now show survey status, clearance and withdraw actions", 4)
    d.page.locator("button:has-text('Clearance')").first.click()
    d.say("Exit clearance checklist: the separation can close only when every item is done", 0)
    box = d.page.locator(".modal input[type=checkbox]").first
    if box.is_enabled() and not box.is_checked():
        box.click()
    d.wait(4)
    d.page.click(".modal button:has-text('Done')")
    d.page.locator("button:has-text('Withdraw')").first.click()
    d.say("Withdraw a resignation: the person stays, and attrition does not change", 4)
    d.page.click(".modal button:has-text('Cancel')")
    if survey_link:
        d.page.goto(survey_link)
        d.page.wait_for_load_state("networkidle")
        d.say("The leaver gets this one-time survey link by email - no login needed", 3)
        for name, n in (("manager", 4), ("pay", 2), ("growth", 3), ("workLife", 4), ("culture", 4)):
            d.page.check(f"input[name={name}][value='{n}']")
            d.wait(0.5)
        d.page.click("button:has-text('Submit')")
        d.say("Submitted once; reusing or expired links are refused", 5)
        d.page.goto(WEB + "/")
        d.page.wait_for_load_state("networkidle")
    d.go("/risk")
    d.say("Weekly flight-risk score (0-100, Low / Medium / High) from tenure, promotion, rating and team attrition", 6)
    d.say("Each score shows its top 3 factors and how much each one contributes", 5)
    d.page.locator("button:has-text('Log action')").first.click()
    d.page.fill(".modal input[list=action-ideas]", "Career discussion")
    d.page.fill(".modal .grid-3 input:not([type])", "Meera Manager")
    d.page.locator(".modal input[type=date]").fill(datetime.date.today().isoformat())
    d.say("Log a retention action with an owner and due date; overdue ones turn red", 3)
    d.page.click(".modal button:has-text('Save')")
    d.wait(1)
    d.scroll(800, 3)
    d.go("/settings")
    d.say("Settings: subscribe to the monthly report email sent on the 2nd", 5)
    # The caption bar covers the bottom of the sidebar, so send the click to the button directly.
    d.page.locator("button:has-text('Sign out')").dispatch_event("click")
    d.login("manager@acme.test")
    d.say("A Department Manager sees only their own department's figures", 6)
    d.go("/risk")
    d.say("...and only their own team's risk scores and retention actions", 6)
    d.say("Also in Sprint 2: survey reminders, HRMS sync, encryption at rest, audit log, load test", 6)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sprint", choices=["sprint1", "sprint2"])
    parser.add_argument("--survey-link")
    args = parser.parse_args()
    work = tempfile.mkdtemp()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1280, "height": 720}, record_video_dir=work,
                                      record_video_size={"width": 1280, "height": 720}, accept_downloads=True)
        page = context.new_page()
        demo = Demo(page)
        if args.sprint == "sprint1":
            sprint1(demo)
        else:
            sprint2(demo, args.survey_link)
        video = page.video.path()
        context.close()
        browser.close()
    out = os.path.join(HERE, args.sprint + ".mp4")
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", video, "-c:v", "libx264",
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", out], check=True)
    shutil.rmtree(work, ignore_errors=True)
    print("wrote", out)


if __name__ == "__main__":
    main()
