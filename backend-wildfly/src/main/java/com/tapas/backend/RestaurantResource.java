package com.tapas.backend;

import com.tapas.backend.model.ErrorResponse;
import com.tapas.backend.model.Message;
import com.tapas.backend.model.RestaurantDetail;
import com.tapas.backend.model.RestaurantSummary;
import com.tapas.backend.model.ReviewInput;
import com.tapas.backend.model.ReviewJson;
import com.tapas.backend.model.ReviewRequest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Path("/restaurants")
public class RestaurantResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response index(@QueryParam("q") String q,
                           @QueryParam("neighborhood") String neighborhood,
                           @QueryParam("food_type") String foodType) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.name, r.neighborhood, r.food_type, r.price_range, " +
                        "(SELECT AVG(rating) FROM reviews WHERE restaurant_id = r.id) AS average_rating " +
                        "FROM restaurants r WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (q != null && !q.trim().isEmpty()) {
            sql.append(" AND (r.name ILIKE ? OR r.neighborhood ILIKE ? OR r.food_type ILIKE ?)");
            String like = "%" + q.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (neighborhood != null && !neighborhood.trim().isEmpty()) {
            sql.append(" AND r.neighborhood ILIKE ?");
            params.add(neighborhood.trim());
        }
        if (foodType != null && !foodType.trim().isEmpty()) {
            sql.append(" AND r.food_type ILIKE ?");
            params.add(foodType.trim());
        }
        sql.append(" ORDER BY r.name");

        List<RestaurantSummary> results = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapSummary(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Response.ok(results).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response show(@PathParam("id") long id) {
        String restaurantSql =
                "SELECT r.id, r.name, r.neighborhood, r.food_type, r.price_range, r.description, r.address, " +
                        "(SELECT AVG(rating) FROM reviews WHERE restaurant_id = r.id) AS average_rating " +
                        "FROM restaurants r WHERE r.id = ?";
        String reviewsSql =
                "SELECT id, author, rating, comment, created_at FROM reviews " +
                        "WHERE restaurant_id = ? ORDER BY created_at DESC";

        try (Connection conn = Db.getConnection()) {
            RestaurantDetail detail;
            try (PreparedStatement stmt = conn.prepareStatement(restaurantSql)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return notFound();
                    }
                    detail = new RestaurantDetail();
                    detail.id = rs.getLong("id");
                    detail.name = rs.getString("name");
                    detail.neighborhood = rs.getString("neighborhood");
                    detail.food_type = rs.getString("food_type");
                    detail.price_range = rs.getString("price_range");
                    detail.description = rs.getString("description");
                    detail.address = rs.getString("address");
                    detail.average_rating = averageRating(rs);
                }
            }

            List<ReviewJson> reviews = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(reviewsSql)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        reviews.add(mapReview(rs));
                    }
                }
            }
            detail.reviews = reviews;

            return Response.ok(detail).build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/{id}/reviews")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listReviews(@PathParam("id") long id) {
        String existsSql = "SELECT 1 FROM restaurants WHERE id = ?";
        String reviewsSql =
                "SELECT id, author, rating, comment, created_at FROM reviews " +
                        "WHERE restaurant_id = ? ORDER BY created_at DESC";

        try (Connection conn = Db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(existsSql)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return notFound();
                    }
                }
            }

            List<ReviewJson> reviews = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(reviewsSql)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        reviews.add(mapReview(rs));
                    }
                }
            }
            return Response.ok(reviews).build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @POST
    @Path("/{id}/reviews")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createReview(@PathParam("id") long id, ReviewRequest body) {
        ReviewInput input = body == null ? null : body.review;
        List<String> errors = validate(input);

        try (Connection conn = Db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM restaurants WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return notFound();
                    }
                }
            }

            if (!errors.isEmpty()) {
                return Response.status(422).entity(new ErrorResponse(errors)).build();
            }

            String insertSql =
                    "INSERT INTO reviews (restaurant_id, author, rating, comment, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, NOW(), NOW()) RETURNING id, author, rating, comment, created_at";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setLong(1, id);
                stmt.setString(2, input.author);
                stmt.setInt(3, input.rating);
                stmt.setString(4, input.comment);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    ReviewJson created = mapReview(rs);
                    return Response.status(Response.Status.CREATED).entity(created).build();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> validate(ReviewInput input) {
        List<String> errors = new ArrayList<>();
        if (input == null || input.author == null || input.author.trim().isEmpty()) {
            errors.add("Author can't be blank");
        }
        if (input == null || input.rating == null) {
            errors.add("Rating can't be blank");
        } else if (input.rating < 1 || input.rating > 5) {
            errors.add("Rating is not included in the list");
        }
        return errors;
    }

    private Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new Message("Restaurant not found"))
                .build();
    }

    private RestaurantSummary mapSummary(ResultSet rs) throws SQLException {
        RestaurantSummary summary = new RestaurantSummary();
        summary.id = rs.getLong("id");
        summary.name = rs.getString("name");
        summary.neighborhood = rs.getString("neighborhood");
        summary.food_type = rs.getString("food_type");
        summary.price_range = rs.getString("price_range");
        summary.average_rating = averageRating(rs);
        return summary;
    }

    private ReviewJson mapReview(ResultSet rs) throws SQLException {
        ReviewJson review = new ReviewJson();
        review.id = rs.getLong("id");
        review.author = rs.getString("author");
        review.rating = rs.getInt("rating");
        review.comment = rs.getString("comment");
        Timestamp createdAt = rs.getTimestamp("created_at");
        review.created_at = createdAt == null ? null : createdAt.toInstant().toString();
        return review;
    }

    private Double averageRating(ResultSet rs) throws SQLException {
        double value = rs.getDouble("average_rating");
        if (rs.wasNull()) {
            return null;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
