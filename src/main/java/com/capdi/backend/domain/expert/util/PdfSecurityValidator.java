package com.capdi.backend.domain.expert.util;

import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.IOException;

@Component
public class PdfSecurityValidator {

    private static final int MAX_PAGE_COUNT = 50;

    public void validate(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            // 1. 암호화 PDF 차단
            if (document.isEncrypted()) {
                System.out.println(
                        "[PDF SECURITY] BLOCKED: Encrypted PDF detected"
                );
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }

            // 2. 페이지가 없는 비정상 PDF 차단
            if (document.getNumberOfPages() <= 0) {
                System.out.println(
                        "[PDF SECURITY] BLOCKED: PDF has no pages"
                );
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            if (document.getNumberOfPages() > MAX_PAGE_COUNT) {
                System.out.println(
                        "[PDF SECURITY] BLOCKED: Too many pages - pages="
                                + document.getNumberOfPages()
                                + ", max="
                                + MAX_PAGE_COUNT
                );

                throw new CustomException(ErrorCode.INVALID_INPUT);
            }

            // 3. OpenAction / JavaScript 검사
            validateNoJavaScript(document);

            // 4. EmbeddedFiles 검사
            validateNoEmbeddedFiles(document);

            // 5. 페이지 Annotation / Action 검사
            validatePageActions(document);

            // 6. Additional Actions 검사
            validateNoAdditionalActions(document);

            // 모든 PDF 보안 검사 통과
            System.out.println(
                    "[PDF SECURITY] PASSED: All PDF security checks passed"
            );

        } catch (InvalidPasswordException e) {

            System.out.println(
                    "[PDF SECURITY] BLOCKED: Encrypted/password-protected PDF detected"
            );

            throw new CustomException(ErrorCode.INVALID_INPUT);

        } catch (IOException e) {

            System.out.println(
                    "[PDF SECURITY] BLOCKED: Invalid or corrupted PDF - "
                            + e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage()
            );

            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateNoJavaScript(PDDocument document) {

        COSDictionary catalog =
                document.getDocumentCatalog().getCOSObject();

        // 8. 문서를 열 때 실행되는 OpenAction 차단
        if (catalog.containsKey(COSName.OPEN_ACTION)) {

            System.out.println(
                    "[PDF SECURITY] BLOCKED: OpenAction detected"
            );

            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 9. Names -> JavaScript 차단
        COSDictionary names =
                catalog.getCOSDictionary(COSName.NAMES);

        if (names != null
                && names.containsKey(COSName.JAVA_SCRIPT)) {

            System.out.println(
                    "[PDF SECURITY] BLOCKED: JavaScript Name Tree detected"
            );

            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateNoEmbeddedFiles(PDDocument document) {

        COSDictionary catalog =
                document.getDocumentCatalog().getCOSObject();

        COSDictionary names =
                catalog.getCOSDictionary(COSName.NAMES);

        if (names == null) {
            return;
        }

        // 10. PDF 내부 첨부파일 차단
        if (names.containsKey(COSName.EMBEDDED_FILES)) {

            System.out.println(
                    "[PDF SECURITY] BLOCKED: EmbeddedFiles detected"
            );

            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePageActions(PDDocument document)
            throws IOException {

        int pageNumber = 0;

        for (PDPage page : document.getPages()) {

            pageNumber++;

            for (PDAnnotation annotation : page.getAnnotations()) {

                // 11. 페이지에 직접 첨부된 파일 차단
                if (annotation instanceof PDAnnotationFileAttachment) {

                    System.out.println(
                            "[PDF SECURITY] BLOCKED: "
                                    + "FileAttachment annotation detected"
                                    + " - page="
                                    + pageNumber
                    );

                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }

                // 12. Link Annotation에 연결된 Action 차단
                if (annotation instanceof PDAnnotationLink link) {

                    PDAction action = link.getAction();

                    if (action != null) {

                        System.out.println(
                                "[PDF SECURITY] BLOCKED: "
                                        + "Link Action detected"
                                        + " - page="
                                        + pageNumber
                                        + ", action="
                                        + action.getClass().getSimpleName()
                        );

                        throw new CustomException(
                                ErrorCode.INVALID_INPUT
                        );
                    }
                }
            }
        }
    }

    private void validateNoAdditionalActions(
            PDDocument document
    ) {

        COSDictionary catalog =
                document.getDocumentCatalog().getCOSObject();

        // 13. Document Catalog /AA 차단
        if (catalog.containsKey(COSName.AA)) {

            System.out.println(
                    "[PDF SECURITY] BLOCKED: "
                            + "Catalog Additional Action (/AA) detected"
            );

            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int pageNumber = 0;

        for (PDPage page : document.getPages()) {

            pageNumber++;

            COSDictionary pageDictionary =
                    page.getCOSObject();

            // 14. Page /AA 차단
            if (pageDictionary.containsKey(COSName.AA)) {

                System.out.println(
                        "[PDF SECURITY] BLOCKED: "
                                + "Page Additional Action (/AA) detected"
                                + " - page="
                                + pageNumber
                );

                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }
    }
}