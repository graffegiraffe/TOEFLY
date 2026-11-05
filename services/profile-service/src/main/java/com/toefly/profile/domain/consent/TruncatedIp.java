package com.toefly.profile.domain.consent;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Keeps the network part of an address only: /24 of IPv4, /48 of IPv6. */
public final class TruncatedIp {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}$");

    private TruncatedIp() {}

    public static @Nullable String of(@Nullable String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String value = address.strip();
        var ipv4 = IPV4.matcher(value);
        if (ipv4.matches()) {
            return ipv4.group(1) + "." + ipv4.group(2) + "." + ipv4.group(3) + ".0/24";
        }
        if (value.contains(":")) {
            String[] groups = value.split(":", -1);
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 3 && i < groups.length; i++) {
                prefix.append(groups[i].isEmpty() ? "0" : groups[i]).append(':');
            }
            return prefix + ":/48";
        }
        return null;
    }
}
