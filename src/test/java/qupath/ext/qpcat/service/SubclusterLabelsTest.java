package qupath.ext.qpcat.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ResultApplier#applySubclusterLabels} names a parent cluster's cells with
 * hierarchical "&lt;parent&gt;.N" classes when re-clustered into sub-types. These
 * are the labels the "Sub-cluster..." action writes onto the detections.
 */
class SubclusterLabelsTest {

    private static PathObject detection() {
        return PathObjects.createDetectionObject(
                ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()));
    }

    @Test
    void appliesHierarchicalParentDotLabelNames() {
        PathObject a = detection();
        PathObject b = detection();
        PathObject c = detection();
        // labels 0,1,0 under parent "Cluster 3" -> "Cluster 3.0" / ".1" / ".0"
        new ResultApplier().applySubclusterLabels(
                List.of(a, b, c), new int[] {0, 1, 0}, "Cluster 3");
        assertThat(a.getPathClass().toString()).isEqualTo("Cluster 3.0");
        assertThat(b.getPathClass().toString()).isEqualTo("Cluster 3.1");
        assertThat(c.getPathClass().toString()).isEqualTo("Cluster 3.0");
    }

    @Test
    void sameSubLabelResolvesToOneClassInstance() {
        PathObject a = detection();
        PathObject c = detection();
        new ResultApplier().applySubclusterLabels(
                List.of(a, c), new int[] {0, 0}, "Cluster 3");
        assertThat(a.getPathClass()).isSameAs(c.getPathClass());
    }

    @Test
    void aRenamedParentIsHonoured() {
        // The parent name is whatever class is on the cells -- e.g. renamed "T cells".
        PathObject a = detection();
        new ResultApplier().applySubclusterLabels(
                List.of(a), new int[] {2}, "T cells");
        assertThat(a.getPathClass().toString()).isEqualTo("T cells.2");
    }

    @Test
    void countMismatchThrows() {
        PathObject a = detection();
        assertThatThrownBy(() -> new ResultApplier().applySubclusterLabels(
                List.of(a), new int[] {0, 1}, "Cluster 3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
