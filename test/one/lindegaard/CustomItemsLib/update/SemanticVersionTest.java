package one.lindegaard.CustomItemsLib.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SemanticVersionTest {

	@Test
	public void stableReleaseIsNewerThanSnapshot() {
		assertTrue(SemanticVersion.parse("4.8.0").compareTo(SemanticVersion.parse("4.8.0-SNAPSHOT+abc123")) > 0);
	}

	@Test
	public void comparesMajorMinorAndPatch() {
		assertTrue(SemanticVersion.parse("8.7.1").compareTo(SemanticVersion.parse("8.7.0")) > 0);
		assertTrue(SemanticVersion.parse("1.4.0").compareTo(SemanticVersion.parse("1.3.9")) > 0);
		assertTrue(SemanticVersion.parse("5.0.0").compareTo(SemanticVersion.parse("4.99.99")) > 0);
	}

	@Test
	public void buildMetadataDoesNotChangePrecedence() {
		assertEquals(0, SemanticVersion.parse("4.8.0+17").compareTo(SemanticVersion.parse("4.8.0+18")));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsFourPartVersions() {
		SemanticVersion.parse("4.8.0.17");
	}
}
