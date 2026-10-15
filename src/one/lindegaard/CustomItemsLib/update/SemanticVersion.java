package one.lindegaard.CustomItemsLib.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small SemVer 2.0 comparator used by the update client.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

	private static final Pattern VERSION_PATTERN = Pattern.compile(
			"^[vV]?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

	private final int major;
	private final int minor;
	private final int patch;
	private final List<String> prerelease;

	private SemanticVersion(int major, int minor, int patch, List<String> prerelease) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.prerelease = prerelease;
	}

	public static SemanticVersion parse(String value) {
		if (value == null)
			throw new IllegalArgumentException("Version is missing");

		Matcher matcher = VERSION_PATTERN.matcher(value.trim());
		if (!matcher.matches())
			throw new IllegalArgumentException("Invalid semantic version: " + value);

		List<String> prerelease = new ArrayList<>();
		if (matcher.group(4) != null) {
			for (String identifier : matcher.group(4).split("\\.")) {
				if (identifier.isEmpty())
					throw new IllegalArgumentException("Invalid semantic version: " + value);
				prerelease.add(identifier);
			}
		}

		return new SemanticVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
				Integer.parseInt(matcher.group(3)), prerelease);
	}

	public boolean isPrerelease() {
		return !prerelease.isEmpty();
	}

	@Override
	public int compareTo(SemanticVersion other) {
		int comparison = Integer.compare(major, other.major);
		if (comparison != 0)
			return comparison;
		comparison = Integer.compare(minor, other.minor);
		if (comparison != 0)
			return comparison;
		comparison = Integer.compare(patch, other.patch);
		if (comparison != 0)
			return comparison;

		if (prerelease.isEmpty())
			return other.prerelease.isEmpty() ? 0 : 1;
		if (other.prerelease.isEmpty())
			return -1;

		for (int i = 0; i < Math.max(prerelease.size(), other.prerelease.size()); i++) {
			if (i >= prerelease.size())
				return -1;
			if (i >= other.prerelease.size())
				return 1;

			String left = prerelease.get(i);
			String right = other.prerelease.get(i);
			boolean leftNumeric = left.chars().allMatch(Character::isDigit);
			boolean rightNumeric = right.chars().allMatch(Character::isDigit);
			if (leftNumeric && rightNumeric) {
				comparison = compareNumericIdentifier(left, right);
			} else if (leftNumeric != rightNumeric) {
				comparison = leftNumeric ? -1 : 1;
			} else {
				comparison = left.compareTo(right);
			}
			if (comparison != 0)
				return comparison;
		}
		return 0;
	}

	private int compareNumericIdentifier(String left, String right) {
		int lengthComparison = Integer.compare(left.length(), right.length());
		return lengthComparison != 0 ? lengthComparison : left.compareTo(right);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (!(object instanceof SemanticVersion))
			return false;
		SemanticVersion other = (SemanticVersion) object;
		return major == other.major && minor == other.minor && patch == other.patch
				&& prerelease.equals(other.prerelease);
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch, prerelease);
	}
}
