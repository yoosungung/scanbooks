import XCTest
@testable import Scanbooks

final class ModelCacheTests: XCTestCase {
    private var tempDir: URL!

    override func setUp() {
        super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("scanbooks-model-cache-\(UUID().uuidString)", isDirectory: true)
        try! FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: tempDir)
        super.tearDown()
    }

    func testDeployPolicyMarksIlltrOnDemand() {
        XCTAssertEqual(ModelDeployPolicy.kind(artifactId: "illtr"), .onDemand)
        XCTAssertEqual(ModelDeployPolicy.kind(artifactId: "geotr"), .residentPreferred)
        XCTAssertEqual(ModelDeployPolicy.kind(artifactId: "seg"), .residentPreferred)
        XCTAssertEqual(ModelDeployPolicy.kind(artifactId: "fingerseg"), .residentPreferred)
        XCTAssertTrue(ModelDeployPolicy.fetchOnLaunch(.residentPreferred))
        XCTAssertFalse(ModelDeployPolicy.fetchOnLaunch(.onDemand))
    }

    func testWorkLongEdgeMitigatedConstant() {
        XCTAssertEqual(PipelineConstants.workLongEdge, 1024)
        XCTAssertEqual(PipelineConstants.workLongEdgeMitigated, 768)
    }

    func testStatusPresentAndChecksumMismatch() throws {
        let bytes = Data("geo-weights".utf8)
        let file = tempDir.appendingPathComponent("geotr.onnx")
        try bytes.write(to: file)
        let good = ModelArtifactSpec(
            id: "geotr",
            file: "geotr.onnx",
            kind: .residentPreferred,
            checksumSha256: ModelCache.sha256Hex(bytes),
            downloadURL: nil
        )
        let bad = ModelArtifactSpec(
            id: "geotr",
            file: "geotr.onnx",
            kind: .residentPreferred,
            checksumSha256: String(repeating: "0", count: 64),
            downloadURL: nil
        )
        let cache = ModelCache(cacheDirectory: tempDir, manifest: ModelManifest(version: "t", artifacts: [good]))
        XCTAssertEqual(cache.status(of: good), .present)
        XCTAssertEqual(cache.status(of: bad), .checksumMismatch)
        XCTAssertEqual(cache.status(of: ModelArtifactSpec(
            id: "illtr", file: "illtr.onnx", kind: .onDemand, checksumSha256: nil, downloadURL: nil
        )), .missing)
    }

    func testEnsureFetchesOnDemandWhenMissing() throws {
        let payload = Data("illtr-body".utf8)
        let expectedHex = ModelCache.sha256Hex(payload)
        let ill = ModelArtifactSpec(
            id: "illtr",
            file: "illtr.onnx",
            kind: .onDemand,
            checksumSha256: expectedHex,
            downloadURL: "https://example.test/illtr.onnx"
        )
        var fetchCount = 0
        let cache = ModelCache(
            cacheDirectory: tempDir,
            manifest: ModelManifest(version: "t", artifacts: [ill])
        ) { _, dest in
            fetchCount += 1
            try payload.write(to: dest)
        }
        let url = try cache.ensure("illtr")
        XCTAssertEqual(fetchCount, 1)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        // Second ensure is cache hit
        _ = try cache.ensure("illtr")
        XCTAssertEqual(fetchCount, 1)
    }

    func testEnsureResidentSkipsOnDemand() throws {
        let geoBytes = Data("geo".utf8)
        let geo = ModelArtifactSpec(
            id: "geotr",
            file: "geotr.onnx",
            kind: .residentPreferred,
            checksumSha256: ModelCache.sha256Hex(geoBytes),
            downloadURL: "https://example.test/geotr.onnx"
        )
        let ill = ModelArtifactSpec(
            id: "illtr",
            file: "illtr.onnx",
            kind: .onDemand,
            checksumSha256: nil,
            downloadURL: "https://example.test/illtr.onnx"
        )
        var fetched: [String] = []
        let cache = ModelCache(
            cacheDirectory: tempDir,
            manifest: ModelManifest(version: "t", artifacts: [geo, ill])
        ) { source, dest in
            fetched.append(source.lastPathComponent)
            if source.lastPathComponent == "geotr.onnx" {
                try geoBytes.write(to: dest)
            } else {
                try Data("ill".utf8).write(to: dest)
            }
        }
        try cache.ensureResidentPreferred()
        XCTAssertEqual(fetched, ["geotr.onnx"])
        XCTAssertTrue(cache.catalog().hasGeo)
        XCTAssertFalse(cache.catalog().hasIll)
    }

    func testEnsureResidentSoftSkipsMissingWithoutUrl() throws {
        let geo = ModelArtifactSpec(
            id: "geotr",
            file: "geotr.onnx",
            kind: .residentPreferred,
            checksumSha256: nil,
            downloadURL: nil
        )
        var fetchCount = 0
        let cache = ModelCache(
            cacheDirectory: tempDir,
            manifest: ModelManifest(version: "t", artifacts: [geo])
        ) { _, dest in
            fetchCount += 1
            try Data("x".utf8).write(to: dest)
        }
        try cache.ensureResidentPreferred()
        XCTAssertEqual(fetchCount, 0)
        XCTAssertFalse(cache.catalog().hasGeo)
    }

    func testParseManifestDeployFields() throws {
        let json = """
        {
          "schema": 1,
          "modelVersion": "v-test",
          "models": {
            "geotr": { "file": "geotr.onnx", "deploy": "residentPreferred", "checksumSha256": null },
            "illtr": { "file": "illtr.onnx", "deploy": "onDemand", "downloadURL": "https://cdn.example/illtr.onnx", "checksumSha256": null }
          }
        }
        """.data(using: .utf8)!
        let m = try ModelManifest.parse(json)
        XCTAssertEqual(m.version, "v-test")
        XCTAssertEqual(m.artifact(id: "geotr")?.kind, .residentPreferred)
        XCTAssertEqual(m.artifact(id: "illtr")?.kind, .onDemand)
        XCTAssertEqual(m.artifact(id: "illtr")?.downloadURL, "https://cdn.example/illtr.onnx")
    }
}
