import Foundation
import PDFKit
import UIKit

enum PdfExporterError: Error, Equatable {
    case noCleanedPages
    case writeFailed
}

/// Builds `export.pdf` from pages that have `cleanedUri` (ARCHITECTURE §6, UIUX §3.3).
struct PdfExporter {
    /// Returns session-relative URI (`export.pdf`).
    func export(sessionId: String, store: SessionStore) throws -> String {
        let session = try store.loadSession(id: sessionId)
        let cleanedRelatives = session.pageIds.compactMap { pageId -> String? in
            (try? store.loadPage(sessionId: sessionId, pageId: pageId))?.cleanedUri
        }
        guard !cleanedRelatives.isEmpty else { throw PdfExporterError.noCleanedPages }

        let document = PDFDocument()
        for (index, relative) in cleanedRelatives.enumerated() {
            let url = store.absoluteFile(sessionId: sessionId, relativeUri: relative)
            guard let image = UIImage(contentsOfFile: url.path),
                  let page = PDFPage(image: image)
            else {
                throw PdfExporterError.writeFailed
            }
            document.insert(page, at: index)
        }

        let relative = "export.pdf"
        let outURL = store.absoluteFile(sessionId: sessionId, relativeUri: relative)
        guard document.write(to: outURL) else { throw PdfExporterError.writeFailed }
        try store.updateExportPdfUri(sessionId: sessionId, relativeUri: relative)
        return relative
    }
}
