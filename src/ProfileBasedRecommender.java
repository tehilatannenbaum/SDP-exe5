import java.util.*;

import static java.util.stream.Collectors.*;
import java.util.stream.Collectors;


/** Profile‑based recommender implementation. */
class ProfileBasedRecommender<T extends Item> extends RecommenderSystem<T> {
    public ProfileBasedRecommender(Map<Integer, User> users,
                                   Map<Integer, T> items,
                                   List<Rating<T>> ratings) {
        super(users, items, ratings);
    }

    @Override
    public List<T> recommendTop10(int userId) {
        Set<Integer> itemsRatedByUser = ratings.stream()
                .filter(r -> r.getUserId() == userId)
                .map(Rating::getItemId)
                .collect(Collectors.toSet());

        List<User> matchingUsers = getMatchingProfileUsers(userId);

        Map<Integer, List<Rating<T>>> ratingsByItem = ratings.stream()
                .filter(r -> matchingUsers.stream()
                        .anyMatch(u -> u.getId() == r.getUserId()))
                //leaves only ratings that are relevant, both matching users and that the
                // rating wes by someone relevant
                .collect(groupingBy(Rating::getItemId));

        return ratingsByItem.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .filter(e -> !itemsRatedByUser.contains(e.getKey()))
                //left with ratings that were rated 5 or more times, and that user didn't rate
                .map(e -> Map.entry(
                        items.get(e.getKey()),
                        e.getValue().stream().mapToDouble(Rating::getRating).average().orElse(0.0)
                        //item, average rating
                ))
                .sorted((e1, e2) -> {
                    int cmp = Double.compare(e2.getValue(), e1.getValue());
                    if (cmp != 0) return cmp;
                    int count1 = ratingsByItem.get(e1.getKey().getId()).size();
                    int count2 = ratingsByItem.get(e2.getKey().getId()).size();
                    cmp = Integer.compare(count2, count1);
                    if (cmp != 0) return cmp;
                    return e1.getKey().getName().compareTo(e2.getKey().getName());
                    //first by average rating, then by amount of ratings and after by name
                })
                .limit(NUM_OF_RECOMMENDATIONS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<User> getMatchingProfileUsers(int userId) {
        User targetUser = users.get(userId);
        return users.values().stream()
                .filter(u -> u.getId() != userId)
                .filter(u -> u.getGender().equals(targetUser.getGender()))
                .filter(u -> Math.abs(u.getAge() - targetUser.getAge()) <= 5)
                .collect(Collectors.toList());
    }
}