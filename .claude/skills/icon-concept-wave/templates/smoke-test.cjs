// Headless check of gallery/review.html against a saved review document, before publishing.
// Usage: node smoke-test.cjs <path to jsdom> <review.html> <saved db doc json>
// Prints how many cards, names rows (done ones hidden) and refinement lists the page shows, and clicks one variant through.
const {JSDOM} = require(process.argv[2]);
const fs = require("fs");
const html = fs.readFileSync(process.argv[3], "utf8");
const saved = JSON.parse(fs.readFileSync(process.argv[4], "utf8"));
delete saved.results; delete saved.updatedAt;
const dom = new JSDOM(html, {runScripts: "dangerously", pretendToBeVisual: true, url: "https://x.test/",
  beforeParse(w) {
    w.scrollTo = () => {};
    w.CSS = {escape: (s) => s.replace(/[^a-zA-Z0-9_-]/g, (c) => "\\" + c)};
    w.localStorage.setItem("concept-icon-review-v1", JSON.stringify(saved));
    w.addEventListener("error", (e) => console.log("PAGE ERROR", e.message));
  }});
const w = dom.window, d = w.document, $ = (i) => d.getElementById(i);
const click = (el) => el.dispatchEvent(new w.MouseEvent("click", {bubbles: true}));
setTimeout(() => {
  console.log("review cards:", d.querySelectorAll("#grid .card").length);
  click($("tab-names"));
  console.log("names rows left:", d.querySelectorAll("#nlist .nrow").length, "|", $("names-intro").textContent.slice(0, 90));
  const row = d.querySelector("#nlist .nrow");
  if (row) {
    click(row.querySelector('[data-vx][data-vf="solid"]'));
    click($("tab-refine"));
    console.log("refine headings:", Array.from(d.querySelectorAll("#rlist h2")).map((e) => e.textContent));
  }
}, 300);
