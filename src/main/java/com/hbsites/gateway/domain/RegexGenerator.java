package com.hbsites.gateway.domain;

import java.util.Objects;

public class RegexGenerator {

    public static String getRegexRange(int endRange) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        int digits = checkIntLength(endRange);
        switch (digits) {
            case 1: regex.append(getRegex(endRange, true));break;
            case 2: {
                char[] digs = String.valueOf(endRange).toCharArray();
                int decimal = Integer.parseInt(String.valueOf(digs[0]));
                int last = Integer.parseInt(String.valueOf(digs[1]));

                regex.append("(");

                regex.append("([1-9])");
                if (endRange > 10) {
                    regex.append("|(");
                    if (endRange % 10 == 0) {
                        regex.append(getRegex(decimal-1, true));
                        regex.append(getRegex(9, false));
                    } else {
                        if (endRange > 20 && endRange < 99) {
                            regex.append(getRegex(decimal-1, true));
                            regex.append(getRegex(9, false));
                            regex.append(")|(");
                        }
                        regex.append(endRange == 99 ? getRegex(decimal, true) : decimal);
                        regex.append(getRegex(last, false));
                    }

                    regex.append(")");
                }

                if (endRange % 10 == 0) {
                    regex.append("|");
                    regex.append("(");
                    regex.append(endRange);
                    regex.append(")");
                }
                regex.append(")");
            }break;
            case 3: {
                // TODO
                regex.append("(([1-9])|([1-9]\\d)|100)");
            }
        }
        regex.append("$");
        return regex.toString();
    }

    private static String getRegex(int number, boolean skipZero) {
        if (skipZero) {
            return getRegexForDigit(number, 1);
        }
        return getRegexForDigit(number, 0);
    }

    private static String getRegexForDigit(Integer number, Integer start) {
        if (Objects.equals(start, number)) {
            return "["+number.toString()+"]";
        }

        if (Objects.equals(start+1, number)) {
            return "[".concat(start.toString().concat(number.toString())).concat("]");
        }

        if (start == 0 && number == 9) {
            return "\\d";
        }

        return "[".concat(start.toString().concat("-").concat(number.toString())).concat("]");
    }

    private static int checkIntLength(int number) {
        if (number < 100000) {
            if (number < 100) {
                if (number < 10) {
                    return 1;
                } else {
                    return 2;
                }
            } else {
                if (number < 1000) {
                    return 3;
                } else {
                    if (number < 10000) {
                        return 4;
                    } else {
                        return 5;
                    }
                }
            }
        } else {
            if (number < 10000000) {
                if (number < 1000000) {
                    return 6;
                } else {
                    return 7;
                }
            } else {
                if (number < 100000000) {
                    return 8;
                } else {
                    if (number < 1000000000) {
                        return 9;
                    } else {
                        return 10;
                    }
                }
            }
        }
    }
}
