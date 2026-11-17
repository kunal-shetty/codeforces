package com.kun.cfdaily.data;

public class Models {

    public static class Problem {
        public String pkey;
        public int contestId;
        public String index;
        public String name;
        public int rating;
        public String[] tags;
        public int solvedCount;
        public boolean solved;
        public boolean bookmarked;
        public String note = "";

        public String url() {
            if (Mode.lc()) return "https://leetcode.com/problems/" + pkey + "/";
            return "https://codeforces.com/problemset/problem/" + contestId + "/" + index;
        }

        /** Short identifier: 1741D on Codeforces, #1741 on LeetCode. */
        public String id() {
            return Mode.lc() ? "#" + contestId : contestId + index;
        }

        public String tagText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tags.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(tags[i]);
            }
            return sb.toString();
        }
    }

    public static class Contest {
        public int id;
        public String name;
        public long startSeconds;
        public long durationSeconds;
        public boolean remind;
        public String slug = "";

        public String url() {
            if (slug.length() > 0) return "https://leetcode.com/contest/" + slug + "/";
            return "https://codeforces.com/contests/" + id;
        }

        /** Page of a finished round, where virtual participation starts. */
        public String pastUrl() {
            if (slug.length() > 0) return "https://leetcode.com/contest/" + slug + "/";
            return "https://codeforces.com/contest/" + id;
        }
    }

    public static class Friend {
        public String handle;
        public int rating;
        public String rank;
        public int week;
        public boolean me;
    }

    public static class RatingPoint {
        public String contestName;
        public long timeSeconds;
        public int oldRating;
        public int newRating;
    }

    public static class DailyItem {
        public String day;
        public int slot;
        public boolean solved;
        public Problem problem;
    }

    public static class TagStat {
        public String tag;
        public int solved;
        public int failed;
        public long ratingSum;
        public int ratedCount;

        public int avgRating() {
            return ratedCount == 0 ? 0 : (int) (ratingSum / ratedCount);
        }

        public double weakness() {
            double failRatio = (failed + 1.0) / (solved + failed + 2.0);
            double exposure = 1.0 / (1.0 + solved / 5.0);
            return 0.5 * failRatio + 0.5 * exposure;
        }
    }
}
