const express = require("express");
const path = require("path");

const app = express();
const PORT = process.env.PORT || 8080;
const BACKEND_URL = process.env.BACKEND_URL || "http://tapas-backend:3000";

app.set("view engine", "ejs");
app.set("views", path.join(__dirname, "views"));
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, "public")));

app.get("/healthz", (req, res) => res.send("ok"));

app.get("/", async (req, res) => {
  const { q = "", neighborhood = "", food_type = "" } = req.query;
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (neighborhood) params.set("neighborhood", neighborhood);
  if (food_type) params.set("food_type", food_type);

  try {
    const response = await fetch(`${BACKEND_URL}/restaurants?${params.toString()}`);
    const restaurants = await response.json();
    res.render("index", { restaurants, q, neighborhood, food_type, error: null });
  } catch (err) {
    console.error("Failed to fetch restaurants", err);
    res.render("index", { restaurants: [], q, neighborhood, food_type, error: "Could not reach the restaurant service." });
  }
});

app.get("/restaurants/:id", async (req, res) => {
  try {
    const response = await fetch(`${BACKEND_URL}/restaurants/${req.params.id}`);
    if (response.status === 404) {
      return res.status(404).render("not_found");
    }
    const restaurant = await response.json();
    res.render("show", { restaurant, error: null, formError: null });
  } catch (err) {
    console.error("Failed to fetch restaurant", err);
    res.render("show", { restaurant: null, error: "Could not reach the restaurant service.", formError: null });
  }
});

app.post("/restaurants/:id/reviews", async (req, res) => {
  const { author, rating, comment } = req.body;

  try {
    const createResponse = await fetch(`${BACKEND_URL}/restaurants/${req.params.id}/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ review: { author, rating, comment } }),
    });

    const detailResponse = await fetch(`${BACKEND_URL}/restaurants/${req.params.id}`);
    const restaurant = await detailResponse.json();

    if (!createResponse.ok) {
      const errorBody = await createResponse.json().catch(() => ({}));
      const formError = (errorBody.errors || ["Could not submit review."]).join(", ");
      return res.render("show", { restaurant, error: null, formError });
    }

    res.redirect(`/restaurants/${req.params.id}`);
  } catch (err) {
    console.error("Failed to submit review", err);
    res.render("show", { restaurant: null, error: "Could not reach the restaurant service.", formError: null });
  }
});

app.listen(PORT, () => {
  console.log(`Tapas finder frontend listening on port ${PORT}, backend at ${BACKEND_URL}`);
});
