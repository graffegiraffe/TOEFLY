package com.toefly.profile.adapter.in.web;

import com.toefly.profile.adapter.in.web.api.PrivacyApi;
import com.toefly.profile.adapter.in.web.dto.AccountDeletion;
import com.toefly.profile.adapter.in.web.dto.DataExport;
import com.toefly.profile.adapter.in.web.dto.DeletionRequest;
import com.toefly.profile.application.port.in.RequestAccountDeletionUseCase;
import com.toefly.profile.application.port.in.RequestDataExportUseCase;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacyController implements PrivacyApi {

    private final RequestDataExportUseCase exports;
    private final RequestAccountDeletionUseCase deletions;
    private final CurrentUser currentUser;

    public PrivacyController(
            RequestDataExportUseCase exports, RequestAccountDeletionUseCase deletions, CurrentUser currentUser) {
        this.exports = exports;
        this.deletions = deletions;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<DataExport> requestDataExport(String idempotencyKey) {
        var export = exports.requestExport(currentUser.get());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/me/data-exports/" + export.id()))
                .body(ProfileDtos.export(export));
    }

    @Override
    public ResponseEntity<DataExport> getDataExport(UUID exportId) {
        return exports.findExport(currentUser.get(), exportId)
                .map(ProfileDtos::export)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("DataExport"));
    }

    @Override
    public ResponseEntity<AccountDeletion> requestAccountDeletion(String idempotencyKey, DeletionRequest request) {
        var deletion = deletions.requestDeletion(currentUser.get(), request.getConfirmation());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ProfileDtos.deletion(deletion));
    }

    @Override
    public ResponseEntity<AccountDeletion> getAccountDeletion() {
        return deletions
                .currentDeletion(currentUser.get())
                .map(ProfileDtos::deletion)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("AccountDeletion"));
    }
}
