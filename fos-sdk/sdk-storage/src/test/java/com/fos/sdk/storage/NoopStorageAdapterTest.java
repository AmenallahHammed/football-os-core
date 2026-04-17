package com.fos.sdk.storage;

import com.fos.sdk.storage.adapter.NoopStorageAdapter;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class NoopStorageAdapterTest {
    private final StoragePort adapter = new NoopStorageAdapter();

    @Test
    void should_return_fake_upload_url_without_any_external_call() {
        var result = adapter.generateUploadUrl("fos-files", "test/doc.pdf",
            "application/pdf", Duration.ofMinutes(15));
        assertThat(result.uploadUrl()).startsWith("https://noop.fos.local/");
        assertThat(result.objectKey()).isEqualTo("test/doc.pdf");
    }

    @Test
    void should_not_throw_on_confirm_or_delete() {
        assertThatCode(() -> adapter.confirmUpload("bucket", "key")).doesNotThrowAnyException();
        assertThatCode(() -> adapter.deleteObject("bucket", "key")).doesNotThrowAnyException();
    }
}
