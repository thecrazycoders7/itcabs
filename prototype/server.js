// itcabs realtime backend — Node stdlib only, no deps.
// Server-authoritative state + SSE push = the shape of Firestore listeners.
// The claim check-and-lock is atomic because Node runs one callback at a time,
// so two concurrent claims can't both win (mirrors the Firestore transaction).
const http = require('http');
const fs = require('fs');
const path = require('path');

const STATUS = { OPEN: 'OPEN', CLAIMED: 'CLAIMED', CONFIRMED: 'CONFIRMED', COMPLETED: 'COMPLETED' };

// --- in-memory store (stands in for Firestore) ---
let legSeq = 1;
const drivers = {
  A: { name: 'Ravi', vehicle: 'Sedan', verified: true, blocked: false },
  B: { name: 'Imran', vehicle: 'SUV', verified: false, blocked: false }, // unverified on purpose
};
const jobs = [
  { id: 1, office: 'DLF Cyber City', shift: 'Logout 18:30', legs: [
    leg('Gachibowli', 'DLF Cyber City', 'Sedan', 420, 4),
    leg('Madhapur', 'DLF Cyber City', 'SUV', 560, 6),
  ] },
];
function leg(pickup, drop, vehicle, fare, seats) {
  return { id: legSeq++, pickup, drop, vehicle, fare: +fare, seats: +seats, status: STATUS.OPEN, claimedBy: null };
}
function findLeg(id) { for (const j of jobs) for (const l of j.legs) if (l.id === id) return l; return null; }
function state() { return { drivers, jobs }; }

// --- SSE clients ---
const clients = new Set();
function broadcast() {
  const data = `data: ${JSON.stringify(state())}\n\n`;
  for (const res of clients) res.write(data);
}

// --- mutations (each returns null on success or an error string) ---
function postJob(office, shift, legs) {
  if (!office || !shift || !legs || !legs.length) return 'office, shift and >=1 leg required';
  jobs.unshift({ id: Date.now(), office, shift,
    legs: legs.map(l => leg(l.pickup, l.drop, l.vehicle, l.fare, l.seats)) });
  return null;
}
function claim(legId, who) {
  const d = drivers[who];
  if (!d) return 'unknown driver';
  if (!d.verified) return `${d.name} not verified`;
  if (d.blocked) return `${d.name} is blocked`;
  const l = findLeg(legId);
  if (!l) return 'no such leg';
  if (l.status !== STATUS.OPEN) return 'leg already taken'; // first-claim-wins
  l.status = STATUS.CLAIMED; l.claimedBy = who;
  return null;
}
function advance(legId, status) {
  const l = findLeg(legId);
  if (!l || !STATUS[status]) return 'bad request';
  l.status = status;
  return null;
}

// --- http ---
const CLIENT = path.join(__dirname, 'index.html');
function body(req) {
  return new Promise(resolve => {
    let b = ''; req.on('data', c => b += c);
    req.on('end', () => { try { resolve(JSON.parse(b || '{}')); } catch { resolve({}); } });
  });
}

http.createServer(async (req, res) => {
  const url = req.url.split('?')[0];

  if (url === '/' || url === '/index.html') {
    return fs.readFile(CLIENT, (e, buf) =>
      e ? (res.writeHead(500), res.end('missing index.html'))
        : (res.writeHead(200, { 'Content-Type': 'text/html' }), res.end(buf)));
  }

  if (url === '/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
    res.write(`data: ${JSON.stringify(state())}\n\n`); // snapshot on connect
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }

  if (req.method === 'POST') {
    const p = await body(req);
    let err = 'not found';
    if (url === '/post') err = postJob(p.office, p.shift, p.legs);
    else if (url === '/claim') err = claim(p.legId, p.who);
    else if (url === '/advance') err = advance(p.legId, p.status);
    if (err === 'not found') { res.writeHead(404); return res.end('not found'); }
    if (err) { res.writeHead(409, { 'Content-Type': 'application/json' }); return res.end(JSON.stringify({ error: err })); }
    broadcast(); // push new state to every viewer
    res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end('{"ok":true}');
  }

  res.writeHead(404); res.end('not found');
}).listen(8777, () => console.log('itcabs realtime on http://localhost:8777'));
