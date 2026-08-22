package io.javalibs.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker =
            new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK);

    @Test
    void masksSensitiveFormFieldsAndKeepsTheRest() {
        assertThat(masker.maskFormEncoded("username=alice&password=hunter2&remember=true"))
                .isEqualTo("username=alice&password=********&remember=true");
    }

    @Test
    void masksTheLastFieldWhenThereIsNoTrailingAmpersand() {
        assertThat(masker.maskFormEncoded("user=a&token=abc"))
                .isEqualTo("user=a&token=********");
    }

    @Test
    void keepsSegmentsThatHaveNoEqualsSign() {
        assertThat(masker.maskFormEncoded("flag&password=x"))
                .isEqualTo("flag&password=********");
    }

    @Test
    void decodesPercentEncodedFieldNamesBeforeMatching() {
        assertThat(masker.maskFormEncoded("api%5Fkey=zzz"))
                .isEqualTo("api%5Fkey=********");
    }

    @Test
    void masksAnEmptyValueSoTheAbsenceOfASecretIsNotLeaked() {
        assertThat(masker.maskFormEncoded("password=&user=a"))
                .isEqualTo("password=********&user=a");
    }

    @Test
    void returnsNullAndEmptyInputUnchanged() {
        assertThat(masker.maskFormEncoded(null)).isNull();
        assertThat(masker.maskFormEncoded("")).isEmpty();
    }

    @Test
    void masksNothingWhenTheKeyPolicyIsNone() {
        SensitiveDataMasker off = new SensitiveDataMasker(SensitiveKeys.none(), null);

        assertThat(off.maskFormEncoded("password=hunter2")).isEqualTo("password=hunter2");
    }

    @Test
    void treatsABlankMaskAsAbsentAndFallsBackToTheDefault() {
        SensitiveDataMasker blank = new SensitiveDataMasker(SensitiveKeys.defaults(), "   ");

        assertThat(blank.maskFormEncoded("password=hunter2")).isEqualTo("password=********");
    }
}
