package com.toir.entity.attachment;

import com.toir.entity.UploadedFile;
import com.toir.enums.AttachmentTargetType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attachment_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 64)
    private AttachmentTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "document_type", length = 64)
    private String documentType;

    @Column(name = "document_number", length = 128)
    private String documentNumber;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNumber ASC")
    private List<AttachmentGroupItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (deleted == null) {
            deleted = false;
        }
    }

    public void addItem(UploadedFile file, int orderNumber, String label) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(AttachmentGroupItem.builder()
                .group(this)
                .file(file)
                .orderNumber(orderNumber)
                .label(label)
                .build());
    }
}
