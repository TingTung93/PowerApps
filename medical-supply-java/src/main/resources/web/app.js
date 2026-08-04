const TOKEN = window.SESSION_TOKEN;

async function api(path, method, body) {
  const res = await fetch(path, {
    method: method || "GET",
    headers: { "X-Session-Token": TOKEN, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || "Request failed");
  return data;
}

function msg(text, ok) {
  const el = document.getElementById("msg");
  el.textContent = text;
  el.style.color = ok === false ? "#b91c1c" : "#0b6a0b";
}

async function refresh() {
  try {
    const s = await api("/api/state");
    const d = s.dashboard || {};
    const tiles = [
      ["SKUs", d.distinctSkus], ["On-hand value", (d.onHandValue || 0).toFixed(2)],
      ["Expired", d.expired], ["Expiring 7d", d.expiring7], ["Expiring 30d", d.expiring30],
      ["Out of stock", d.outOfStock], ["Stale", d.stale]
    ];
    document.getElementById("dashboard").innerHTML =
      tiles.map(t => `<div class="tile"><b>${t[1] ?? 0}</b>${t[0]}</div>`).join("");
    const rows = (s.stock || []).map(l =>
      `<tr><td>${esc(l.name)}</td><td>${esc(l.gtin)}</td><td>${esc(l.lot)}</td><td>${esc(l.expirationIso)}</td><td>${l.quantity}</td></tr>`);
    document.querySelector("#stock tbody").innerHTML = rows.join("");
    if (s.sharedRoot) document.getElementById("root").value = s.sharedRoot;
  } catch (e) { msg(e.message, false); }
}

async function configure() {
  try { await api("/api/configure", "POST", { sharedRoot: document.getElementById("root").value }); msg("Folder set."); refresh(); }
  catch (e) { msg(e.message, false); }
}

async function receive() {
  const raw = document.getElementById("raw").value;
  const quantity = document.getElementById("qty").value;
  try {
    const r = await api("/api/receive", "POST", { raw, quantity, force: "false" });
    if (r.needsRegistration) {
      const name = prompt("Unknown product " + r.gtin + ". Product name to register:");
      if (!name) return;
      const sug = r.suggestion || {};
      await api("/api/register", "POST", { gtin: r.gtin, name, manufacturer: sug.manufacturer || "", category: sug.category || "", source: sug.found ? "GUDID" : "MANUAL" });
      await api("/api/receive", "POST", { raw, quantity, force: "true" });
    }
    msg("Received.");
    document.getElementById("raw").value = "";
    refresh();
  } catch (e) { msg(e.message, false); }
}

async function report() {
  try { const r = await api("/api/report", "POST", {}); msg("Report: " + r.htmlFile); }
  catch (e) { msg(e.message, false); }
}

function esc(v) { return (v || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }

refresh();
setInterval(refresh, 15000);
