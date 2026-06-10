package com.toir.enums;

public enum FileCategory {
    USER_AVATAR("user-avatars"),
    PASSPORT("passports"),
    CONTRACT("contracts"),
    PRODUCT_IMAGE("product-images"),
    VEHICLE_DOCUMENT("vehicle-documents"),
    VEHICLE_PICTURE("vehicle-pictures"),
    EQUIPMENT_DOCUMENT("equipment-documents"),
    EQUIPMENT_PICTURE("equipment-pictures"),
    WORK_ORDER_DOCUMENT("work-order-documents"),
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
