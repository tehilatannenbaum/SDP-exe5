import java.util.*;
import java.util.stream.Collectors;

/** Popularity‑based recommender implementation. */
class PopularityBasedRecommender<T extends Item> extends RecommenderSystem<T> {
    private static final int POPULARITY_THRESHOLD = 100;
    public PopularityBasedRecommender(Map<Integer, User> users,
                                      Map<Integer, T> items,
                                      List<Rating<T>> ratings) {
        super(users, items, ratings);
    }

    @Override
    public List<T> recommendTop10(int userId) {
        //first we need to find all the items that the user rated
        Set<Integer> itemsRatedByUser = ratings.stream()
                .filter(r -> r.getUserId() == userId)
                .map(Rating::getItemId)
                .collect(Collectors.toSet());

        //filter items 1-not rated bu user 2-rating more than 100
        return items.values().stream()
                .filter(item -> !itemsRatedByUser.contains(item.getId()))
                .filter(item -> getItemRatingsCount(item.getId()) >= 100)
                .sorted((i1, i2) -> {
                    double avg1 = getItemAverageRating(i1.getId());
                    double avg2 = getItemAverageRating(i2.getId());
                    int cmp = Double.compare(avg2, avg1); //avg2-avg1
                    if (cmp != 0) return cmp;

                    int count1 = getItemRatingsCount(i1.getId());
                    int count2 = getItemRatingsCount(i2.getId());
                    cmp = Integer.compare(count2, count1);
                    if (cmp != 0) return cmp;

                    return i1.getName().compareTo(i2.getName());
                    //first by rating average (high to low), and then by amount of ratings
                    //and then by name
                })
                .limit(NUM_OF_RECOMMENDATIONS)
                .collect(Collectors.toList());
    }

    public double getItemAverageRating(int itemId) {
        return ratings.stream()
                .filter(r -> r.getItemId() == itemId)
                .mapToDouble(Rating::getRating)
                .average()
                .orElse(0.0);
        //returns the average of ratings
    }
    public int getItemRatingsCount(int itemId) {
        return (int) ratings.stream()
                .filter(r -> r.getItemId() == itemId)
                .count();
        //returns the amount of ratings for item
    }

}