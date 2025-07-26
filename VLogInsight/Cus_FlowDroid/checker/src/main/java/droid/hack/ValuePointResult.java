package droid.hack;

import java.util.HashSet;
import java.util.Set;

public class ValuePointResult {
    private Set<String> possibleValues = new HashSet<>();
    public void addPossibleValue(Set<String> value) {
        this.possibleValues.addAll(value);
    }

    public boolean hasResult() {
        return this.possibleValues.size() > 0;
    }

    public boolean isAllEmptyString() {
        for (String tmpString : this.possibleValues) {
            if (tmpString.length() > 0) return false;
        }
        return true;
    }

    public Set<String> getPossibleValues() {
        return possibleValues;
    }
}
