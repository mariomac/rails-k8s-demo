const FRONTEND_URL = process.env.FRONTEND_URL || "http://tapas-frontend:8080";
const MIN_INTERVAL_MS = Number(process.env.MIN_INTERVAL_MS || 350);
const MAX_INTERVAL_MS = Number(process.env.MAX_INTERVAL_MS || 500);

const NEIGHBORHOODS = ["El Born", "Poble Sec", "Eixample", "El Raval", "La Barceloneta", "La Boqueria"];
const FOOD_TYPES = ["Traditional Tapas", "Seafood Tapas", "Modern Tapas", "Gourmet Tapas", "Basque Tapas"];
const NAMES = ["Anna", "Marc", "Laura", "Jordi", "Elena", "David", "Nuria", "Pau", "Cristina", "Sergi"];
const COMMENTS = [
  "Great atmosphere and friendly staff.",
  "Would definitely come back for the patatas bravas.",
  "A bit crowded but worth it.",
  "Loved the local wine pairing.",
  "Perfect spot for a quick tapas crawl stop.",
];

function randomItem(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function randomInterval() {
  return MIN_INTERVAL_MS + Math.random() * (MAX_INTERVAL_MS - MIN_INTERVAL_MS);
}

async function browse() {
  const filters = [
    {},
    { q: randomItem(["bar", "tapas", "cal", "el"]) },
    { neighborhood: randomItem(NEIGHBORHOODS) },
    { food_type: randomItem(FOOD_TYPES) },
  ];
  const params = new URLSearchParams(randomItem(filters));

  const listResponse = await fetch(`${FRONTEND_URL}/?${params.toString()}`);
  await listResponse.text();

  const restaurantId = 1 + Math.floor(Math.random() * 15);
  const detailResponse = await fetch(`${FRONTEND_URL}/restaurants/${restaurantId}`);
  await detailResponse.text();

  if (Math.random() < 0.2) {
    await fetch(`${FRONTEND_URL}/restaurants/${restaurantId}/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        author: randomItem(NAMES),
        rating: String(1 + Math.floor(Math.random() * 5)),
        comment: randomItem(COMMENTS),
      }).toString(),
    });
  }
}

async function tick() {
  try {
    await browse();
  } catch (err) {
    console.error("Load generator request failed:", err.message);
  } finally {
    setTimeout(tick, randomInterval());
  }
}

console.log(`Load generator starting against ${FRONTEND_URL}`);
tick();
