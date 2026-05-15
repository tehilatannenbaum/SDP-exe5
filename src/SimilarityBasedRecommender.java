import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;


/** Similarity‑based recommender with bias correction. */
class SimilarityBasedRecommender<T extends Item> extends RecommenderSystem<T> {
    // TODO: add data structures to hold the global/item/user biases
    private final double globalBias;
    private final Map<Integer, Double> itemBiases;
    private final Map<Integer, Double> userBiases;

    public SimilarityBasedRecommender(Map<Integer, User> users,
                                      Map<Integer, T> items,
                                      List<Rating<T>> ratings) {
        super(users, items, ratings);
        //average of all the ratings
        this.globalBias = ratings.stream()
                .mapToDouble(Rating::getRating)
                .average()
                .orElse(0.0);

        Map<Integer, List<Double>> itemToBiasFreeRatings = ratings.stream()
                .collect(groupingBy(Rating::getItemId,
                        mapping(r -> r.getRating() - globalBias, toList())));
        //takes rating by item ID, and removes bias
        this.itemBiases = itemToBiasFreeRatings.entrySet().stream()
                .collect(toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)));
        //calc average of rating without bias
        Map<Integer, List<Double>> userToBiasFreeRatings = ratings.stream()
                .map(r -> {
                    double itemBias = itemBiases.getOrDefault(r.getItemId(), 0.0);
                    double biasFree = r.getRating() - globalBias - itemBias;
                    return Map.entry(r.getUserId(), biasFree);
                })
                //went over each rating and take off global and item bias
                .collect(groupingBy(Map.Entry::getKey,
                        mapping(Map.Entry::getValue, toList())));
        //collected all rating without bias by user ID
        this.userBiases = userToBiasFreeRatings.entrySet().stream()
                .collect(toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)));
        //for each user calc its rating
    }


    /** Dot‑product similarity; 0 if <10 shared items. */
    public double getSimilarity(int u1, int u2) {
        Map<Integer, Double> ratings1 = ratings.stream()
                .filter(r -> r.getUserId() == u1)
                .collect(toMap(Rating::getItemId, Rating::getRating));

        Map<Integer, Double> ratings2 = ratings.stream()
                .filter(r -> r.getUserId() == u2)
                .collect(toMap(Rating::getItemId, Rating::getRating));

        Set<Integer> sharedItems = ratings1.keySet().stream()
                .filter(ratings2::containsKey)
                .collect(toSet());
        //looks for shared keys

        if (sharedItems.size() < 10) return 0.0;

        double dotProduct = sharedItems.stream()
                .mapToDouble(itemId -> {
                    double r1 = ratings1.get(itemId);
                    double r2 = ratings2.get(itemId);
                    double iBias = itemBiases.getOrDefault(itemId, 0.0);
                    double u1Bias = userBiases.getOrDefault(u1, 0.0);
                    double u2Bias = userBiases.getOrDefault(u2, 0.0);

                    double adj1 = r1 - globalBias - iBias - u1Bias;
                    double adj2 = r2 - globalBias - iBias - u2Bias;

                    return adj1 * adj2;
                })
                .sum();

        return dotProduct;
    }


    @Override public List<T> recommendTop10(int userId){
        Set<Integer> ratedItems = ratings.stream()
                .filter(r -> r.getUserId() == userId)
                .map(Rating::getItemId)
                .collect(toSet());
        //items user rated already

        // finding 10 most similar users
        List<Integer> top10SimilarUsers = users.keySet().stream()
                .filter(otherId -> otherId != userId)
                .map(otherId -> Map.entry(otherId, getSimilarity(userId, otherId)))
                //only positive
                .filter(entry -> entry.getValue() > 0)
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(NUM_OF_RECOMMENDATIONS)
                .map(Map.Entry::getKey)
                .collect(toList());

        // returns empty if no similar
        if (top10SimilarUsers.isEmpty()) {
            return List.of();
        }

        Map<Integer, Double> similarities = top10SimilarUsers.stream()
                //.filter(otherId -> otherId != userId)
                .collect(toMap(
                        Function.identity(),
                        otherId -> getSimilarity(userId, otherId)
                ));
        //find similarities with different users

        Map<Integer, List<Rating<T>>> ratingsByItem = ratings.stream()
                .filter(r -> top10SimilarUsers.contains(r.getUserId()))
                .collect(groupingBy(Rating::getItemId));
        //mapped by item id

        return items.values().stream()
                .filter(item -> !ratedItems.contains(item.getId()))
                //for each item we'll calc the estimated rating
                .map(item -> {
                    int itemId = item.getId();
                    List<Rating<T>> itemRatings = ratingsByItem.getOrDefault(itemId,List.of());
                    //itemId, all the rating this item got

                    // at least 5 users rated the item
                    if (itemRatings.size() < 5) {
                        return null;
                    }

                    double numerator = itemRatings.stream()
                            .filter(r -> similarities.getOrDefault(r.getUserId(), 0.0) > 0)
                            .mapToDouble(r -> {
                                int v = r.getUserId();
                                double sim = similarities.getOrDefault(v, 0.0);
                                double biasV = userBiases.getOrDefault(v, 0.0);
                                double itemBias = itemBiases.getOrDefault(itemId, 0.0);
                                double adjustedRating = r.getRating() - globalBias - itemBias - biasV;

                                return sim * adjustedRating;
                            })
                            .sum();

                    double denominator = itemRatings.stream()
                            .mapToDouble(r -> similarities.getOrDefault(r.getUserId(), 0.0))
                            .filter(sim -> sim > 0)
                            .map(Math::abs)
                            .sum();

                    //if similar users rated item then add
                    double itemBias = itemBiases.getOrDefault(itemId, 0.0);
                    double userBias = userBiases.getOrDefault(userId, 0.0);
                    double predicted = globalBias + itemBias   + userBias;
                    if (denominator != 0)
                        predicted += numerator / denominator;

                    return Map.entry(item, predicted);
                })

                //only items that were rated 5 times
                .filter(Objects::nonNull)

                //first by predicted rating and then by name
                .sorted((e1, e2) -> {
                    int cmp = Double.compare(e2.getValue(), e1.getValue());
                    if (cmp != 0) return cmp;
                    return e1.getKey().getName().compareTo(e2.getKey().getName());
                })
                .limit(NUM_OF_RECOMMENDATIONS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public double getGlobalBias() {
        return globalBias;
    }
    public double getItemBias(int itemId) {
        return itemBiases.getOrDefault(itemId, 0.0);
    }

    public double getUserBias(int userId) {
        return userBiases.getOrDefault(userId, 0.0);
    }

    public void printGlobalBias() {
        // TODO: fix
        System.out.println("Global bias: " + String.format("%.2f", globalBias));
    }

    public void printItemBias(int itemId) {
        double itemBias = itemBiases.getOrDefault(itemId, 0.0);
        System.out.println("Item bias for item " + itemId + ": " + String.format("%.2f", itemBias));
    }
    public void printUserBias(int userId) {
        double userBias = userBiases.getOrDefault(userId, 0.0);
        System.out.println("User bias for user " + userId + ": " + String.format("%.2f",userBias));
    }
}