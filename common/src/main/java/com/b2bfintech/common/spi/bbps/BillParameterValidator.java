package com.b2bfintech.common.spi.bbps;

import com.b2bfintech.common.exception.BadRequestException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks a customer's submitted fields against the parameter list a biller declared. The account
 * identity is whichever declared name starts with {@code utility_acc_no} (some billers use
 * {@code utility_acc_no2}).
 */
public final class BillParameterValidator {

    private BillParameterValidator() {
    }

    public static ValidatedParameters validate(Map<String, String> submitted, List<BbpsProvider.ProviderBillerParameter> declared) {
        if (declared == null || declared.isEmpty()) {
            throw new BadRequestException("This biller does not publish any bill fields, so a bill cannot be fetched");
        }
        Map<String, String> values = submitted == null ? Map.of() : submitted;
        Map<String, BbpsProvider.ProviderBillerParameter> byName = new LinkedHashMap<>();
        for (BbpsProvider.ProviderBillerParameter parameter : declared) {
            byName.put(parameter.name(), parameter);
        }
        for (String name : values.keySet()) {
            if (!byName.containsKey(name)) {
                throw new BadRequestException("Unexpected field '" + name + "'");
            }
        }
        String accountNumber = null;
        for (BbpsProvider.ProviderBillerParameter parameter : declared) {
            String value = values.get(parameter.name());
            if (value == null || value.isBlank()) {
                throw new BadRequestException(messageFor(parameter, parameter.label() + " is required"));
            }
            String regex = parameter.validationRegex();
            if (regex != null && !regex.isBlank() && !matches(regex, value)) {
                throw new BadRequestException(messageFor(parameter, parameter.label() + " is not in the expected format"));
            }
            if (parameter.name() != null && parameter.name().startsWith("utility_acc_no") && accountNumber == null) {
                accountNumber = value.trim();
            }
        }
        if (accountNumber == null) {
            throw new BadRequestException("This biller has no account number field, so a bill cannot be fetched");
        }
        return new ValidatedParameters(accountNumber, Map.copyOf(values));
    }

    private static boolean matches(String regex, String value) {
        try {
            return Pattern.compile(regex).matcher(value).matches();
        } catch (PatternSyntaxException ex) {
            return true;
        }
    }

    private static String messageFor(BbpsProvider.ProviderBillerParameter parameter, String fallback) {
        return parameter.errorMessage() != null && !parameter.errorMessage().isBlank()
                ? parameter.errorMessage()
                : fallback;
    }

    public record ValidatedParameters(String accountNumber, Map<String, String> parameters) {
    }
}
