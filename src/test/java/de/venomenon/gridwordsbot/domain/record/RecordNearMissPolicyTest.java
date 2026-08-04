package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RecordNearMissPolicyTest {

    @ParameterizedTest
    @MethodSource("windows")
    void calculatesTheUncappedRoundedUpTenPercentWindow(int reference, int expectedWindow) {
        assertThat(RecordNearMissPolicy.window(reference)).isEqualTo(expectedWindow);
    }

    @ParameterizedTest
    @MethodSource("nearMisses")
    void classifiesOnlyStrictLossesInsideTheWindow(int reference, int candidate, boolean expected) {
        assertThat(RecordNearMissPolicy.isNearMiss(reference, candidate)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("invalidLengths")
    void rejectsNonPositiveLengths(int reference, int candidate) {
        if (reference <= 0) {
            assertThatIllegalArgumentException().isThrownBy(() -> RecordNearMissPolicy.window(reference));
        } else {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> RecordNearMissPolicy.isNearMiss(reference, candidate));
        }
    }

    static Stream<Arguments> windows() {
        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(7, 1),
                Arguments.of(10, 1),
                Arguments.of(15, 2),
                Arguments.of(30, 3),
                Arguments.of(80, 8),
                Arguments.of(120, 12));
    }

    static Stream<Arguments> nearMisses() {
        return Stream.of(
                Arguments.of(7, 6, true),
                Arguments.of(7, 5, false),
                Arguments.of(15, 13, true),
                Arguments.of(15, 12, false),
                Arguments.of(80, 72, true),
                Arguments.of(80, 71, false),
                Arguments.of(80, 80, false),
                Arguments.of(80, 81, false));
    }

    static Stream<Arguments> invalidLengths() {
        return Stream.of(
                Arguments.of(0, 1),
                Arguments.of(-1, 1),
                Arguments.of(10, 0),
                Arguments.of(10, -1));
    }
}
