import CryptoKit
import Foundation

/// Resident vs on-demand deploy kind (ARCHITECTURE §1.1 / §5.4; M1 latency → IllTr on-demand).
enum ModelDeployKind: String, Equatable, Sendable {
    case residentPreferred
    case onDemand
}

enum ModelDeployPolicy {
    static func kind(artifactId: String) -> ModelDeployKind {
        switch artifactId {
        case "illtr": return .onDemand
        default: return .residentPreferred
        }
    }

    /// Launch/warmup fetch only for resident-preferred artifacts.
    static func fetchOnLaunch(_ kind: ModelDeployKind) -> Bool {
        kind == .residentPreferred
    }
}

struct ModelArtifactSpec: Equatable, Sendable {
    var id: String
    var file: String
    var kind: ModelDeployKind
    var checksumSha256: String?
    var downloadURL: String?
}

struct ModelManifest: Equatable, Sendable {
    var version: String
    var artifacts: [ModelArtifactSpec]

    func artifact(id: String) -> ModelArtifactSpec? {
        artifacts.first { $0.id == id }
    }

    static func parse(_ data: Data) throws -> ModelManifest {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ModelCacheError.invalidManifest
        }
        let version = (root["modelVersion"] as? String) ?? "unknown"
        guard let models = root["models"] as? [String: Any] else {
            throw ModelCacheError.invalidManifest
        }
        var artifacts: [ModelArtifactSpec] = []
        for (id, raw) in models {
            guard let obj = raw as? [String: Any], let file = obj["file"] as? String else { continue }
            let deployRaw = obj["deploy"] as? String
            let kind = deployRaw.flatMap(ModelDeployKind.init(rawValue:)) ?? ModelDeployPolicy.kind(artifactId: id)
            let checksum = obj["checksumSha256"] as? String
            let url = obj["downloadURL"] as? String
            artifacts.append(
                ModelArtifactSpec(
                    id: id,
                    file: file,
                    kind: kind,
                    checksumSha256: checksum,
                    downloadURL: url
                )
            )
        }
        artifacts.sort { $0.id < $1.id }
        return ModelManifest(version: version, artifacts: artifacts)
    }
}

enum ModelCacheStatus: Equatable, Sendable {
    case present
    case missing
    case checksumMismatch
}

enum ModelCacheError: Error, Equatable {
    case unknownArtifact(String)
    case missingDownloadURL(String)
    case checksumMismatch(String)
    case invalidManifest
    case fetchFailed(String)
}

/// On-device ONNX cache: ensure/checksum + OrtModelCatalog (ARCHITECTURE §1.10).
struct ModelCache: Sendable {
    typealias Fetcher = @Sendable (URL, URL) throws -> Void

    var cacheDirectory: URL
    var manifest: ModelManifest
    var fetcher: Fetcher
    var defaultOrtEp: String

    init(
        cacheDirectory: URL,
        manifest: ModelManifest,
        defaultOrtEp: String = PipelineConstants.defaultOrtEpIOS,
        fetcher: @escaping Fetcher = ModelCache.defaultFetcher
    ) {
        self.cacheDirectory = cacheDirectory
        self.manifest = manifest
        self.defaultOrtEp = defaultOrtEp
        self.fetcher = fetcher
    }

    static func defaultFetcher(source: URL, dest: URL) throws {
        let data = try Data(contentsOf: source)
        try data.write(to: dest, options: .atomic)
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    func status(of artifact: ModelArtifactSpec) -> ModelCacheStatus {
        let url = cacheDirectory.appendingPathComponent(artifact.file)
        guard FileManager.default.fileExists(atPath: url.path) else { return .missing }
        guard let expected = artifact.checksumSha256, !expected.isEmpty else { return .present }
        guard let data = try? Data(contentsOf: url) else { return .missing }
        return Self.sha256Hex(data).caseInsensitiveCompare(expected) == .orderedSame
            ? .present
            : .checksumMismatch
    }

    @discardableResult
    func ensure(_ artifactId: String) throws -> URL {
        guard let artifact = manifest.artifact(id: artifactId) else {
            throw ModelCacheError.unknownArtifact(artifactId)
        }
        let dest = cacheDirectory.appendingPathComponent(artifact.file)
        switch status(of: artifact) {
        case .present:
            return dest
        case .checksumMismatch:
            try? FileManager.default.removeItem(at: dest)
            fallthrough
        case .missing:
            guard let urlString = artifact.downloadURL, let source = URL(string: urlString) else {
                throw ModelCacheError.missingDownloadURL(artifactId)
            }
            try FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
            do {
                try fetcher(source, dest)
            } catch {
                throw ModelCacheError.fetchFailed(artifactId)
            }
            if status(of: artifact) == .checksumMismatch {
                try? FileManager.default.removeItem(at: dest)
                throw ModelCacheError.checksumMismatch(artifactId)
            }
            return dest
        }
    }

    /// Warm resident-preferred weights only (IllTr stays on-demand).
    /// Missing file + nil `downloadURL` → soft-skip (passthrough until CDN/LFS wired).
    func ensureResidentPreferred() throws {
        for artifact in manifest.artifacts where ModelDeployPolicy.fetchOnLaunch(artifact.kind) {
            if status(of: artifact) == .present { continue }
            guard !(artifact.downloadURL ?? "").isEmpty else { continue }
            _ = try ensure(artifact.id)
        }
    }

    func catalog() -> OrtModelCatalog {
        OrtModelCatalog(
            version: manifest.version,
            geotrFile: manifest.artifact(id: "geotr")?.file ?? "geotr.onnx",
            illtrFile: manifest.artifact(id: "illtr")?.file ?? "illtr.onnx",
            fingersegFile: manifest.artifact(id: "fingerseg")?.file ?? "fingerseg.onnx",
            defaultOrtEp: defaultOrtEp,
            modelsDirectory: cacheDirectory
        )
    }
}
