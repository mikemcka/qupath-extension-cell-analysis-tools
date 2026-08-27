"""Tests for the PCA-precursor gating helper in run_clustering.py.

The precursor reduces a high-feature matrix to principal components before the
embedding + clustering step (the canonical scanpy flow). It is driven by a
per-run checkbox ("Reduce features with PCA before clustering", ticked by
default) and only actually engages when the feature count exceeds the target
component count. That decision is a pure top-level function, so we exercise the
real shipped code through the AST loader.
"""

from conftest import load_script_symbol

resolve = load_script_symbol("run_clustering.py", "_resolve_pca_precursor")


def test_enabled_engages_when_features_exceed_components():
    # 442 features (2 markers x 34 compartments style), 50 components -> reduce.
    assert resolve(True, 442, 50, "leiden") is True


def test_enabled_skips_when_features_at_or_below_components():
    # Nothing worth reducing: the target dimensionality is the engage floor.
    assert resolve(True, 50, 50, "leiden") is False
    assert resolve(True, 12, 50, "kmeans") is False


def test_disabled_never_engages_even_when_huge():
    # Checkbox unticked.
    assert resolve(False, 442, 50, "leiden") is False


def test_banksy_is_always_exempt():
    # BANKSY runs its own PCA over spatially-augmented features; a generic
    # precursor would corrupt that, so it is exempt even when ticked.
    assert resolve(True, 442, 50, "banksy") is False


def test_too_few_features_never_reduces():
    # Nothing meaningful to reduce at or below 2 features.
    assert resolve(True, 2, 1, "leiden") is False


def test_custom_component_count_moves_the_floor():
    # A smaller component target lowers the threshold at which it engages.
    assert resolve(True, 30, 20, "kmeans") is True
    assert resolve(True, 30, 30, "kmeans") is False
