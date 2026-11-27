package com.capdi.backend.domain.payment.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class PortOneSecretFilesTest {
    @TempDir Path directory;
    @Test void defaultAbsentAndLegacyInlineRemainCompatible() {
        var e=new MockEnvironment();assertThat(PortOneSecretFiles.resolve(e,"api-secret")).isNull();
        e.setProperty("payment.portone.api-secret","synthetic-only");
        assertThat(PortOneSecretFiles.resolve(e,"api-secret")).isEqualTo("synthetic-only");
    }
    @Test void mountedFileWithSingleFinalNewlineIsAccepted() throws Exception {
        var p=directory.resolve("api");Files.writeString(p,"synthetic-only\r\n");
        var e=new MockEnvironment().withProperty("payment.portone.api-secret-file",p.toString());
        assertThat(PortOneSecretFiles.resolve(e,"api-secret")).isEqualTo("synthetic-only");
    }
    @Test void conflictingInlineAndFileFailWithoutDisclosure() throws Exception {
        var p=directory.resolve("api");Files.writeString(p,"synthetic-only");
        var e=new MockEnvironment().withProperty("payment.portone.api-secret-file",p.toString())
            .withProperty("payment.portone.api-secret","synthetic-conflict");
        rejected(e);
    }
    @Test void missingRelativeDirectoryAndOversizedFilesFailClosed() throws Exception {
        for(String path:new String[]{directory.resolve("missing").toString(),"relative",directory.toString()})
            rejected(new MockEnvironment().withProperty("payment.portone.api-secret-file",path));
        var p=directory.resolve("big");Files.writeString(p,"x".repeat(5000));
        rejected(new MockEnvironment().withProperty("payment.portone.api-secret-file",p.toString()));
    }
    @Test void whitespaceBinaryAndMultilineAreRejected() throws Exception {
        var p=directory.resolve("api");
        for(String value:new String[]{""," ","synthetic\nsecond","synthetic\n\n","\u00e9","synthetic\u0000"}) {
            Files.writeString(p,value);
            rejected(new MockEnvironment().withProperty("payment.portone.api-secret-file",p.toString()));
        }
    }
    private void rejected(MockEnvironment e) {
        assertThatThrownBy(()->PortOneSecretFiles.resolve(e,"api-secret"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PortOne secret file configuration invalid")
            .hasNoCause();
    }
}
