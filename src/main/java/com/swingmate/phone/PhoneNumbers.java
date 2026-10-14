package com.swingmate.phone;

import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import java.util.regex.Pattern;

/** 명세서 6장: 공백·하이픈을 제거한 숫자 11자리 01x 형식으로 비교·저장한다. */
public final class PhoneNumbers {

    private static final Pattern MOBILE = Pattern.compile("01\\d{9}");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (!MOBILE.matcher(digits).matches()) {
            throw new ApiException(ErrorCode.PHONE_NUMBER_INVALID);
        }
        return digits;
    }
}
