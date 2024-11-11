package com.example.legacyx.utils;

public enum FunctionParameters {
    TOTAL_BALANCE(1),
    RESERVED_BALANCE(2),
    AVAILABLE_BALANCE(3);

    private final int value;

    FunctionParameters(int value) {
        this.value = value;
    }

    public static void printBalanceTypes(){
        System.out.println("1. Total Balance");
        System.out.println("2. Reserved Balance");
        System.out.println("3. Available Balance");
    }

    public static FunctionParameters getBalanceOptions(int value) {
        switch (value) {
            case 1 -> {
                return FunctionParameters.TOTAL_BALANCE;
            }
            case 2 -> {
                return FunctionParameters.RESERVED_BALANCE;
            }
            case 3 -> {
                return FunctionParameters.AVAILABLE_BALANCE;
            }
            default -> {
                return null;
            }
        }
    }

    public static String getBalanceText(int value) {
        switch (value) {
            case 1 -> {
                return "Total balance";
            }
            case 2 -> {
                return "Reserved balance";
            }
            case 3 -> {
                return "Available balance";
            }
            default -> {
                return null;
            }
        }
    }

    public int getValue() {
        return value;
    }
}
