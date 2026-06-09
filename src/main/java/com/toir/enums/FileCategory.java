package com.toir.enums;

public enum FileCategory {
    USER_AVATAR("user-avatars"),
    PASSPORT("passports"),
    CONTRACT("contracts"),
    PRODUCT_IMAGE("product-images"),
    VEHICLE_DOCUMENT("vehicle-documents"),
    EQUIPMENT_DOCUMENT("equipment-documents"),
    CHAT_ATTACHMENT("chat-attachments"),
    DOCUMENT("documents"),
    OTHER("other");

    private final String folder;

    FileCategory(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
