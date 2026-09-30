# P2P LAN File Search & Share Engine

A desktop peer-to-peer file search and transfer application for a local-area network. Every device runs the same Java application: it advertises itself through UDP multicast, indexes files in a local shared folder, answers search requests over TCP, and can download a selected result directly from the peer that owns it.

The project is intended for trusted LANs such as a home Wi-Fi network, lab network, or mobile hotspot. It does not require a central server, database, or internet connection once the dependencies have been built.

## What it does

- Automatically discovers peers on the same multicast-capable LAN.
- Watches a local share folder and rebuilds its full-text index after file changes.
- Searches both file names and extracted file contents with Apache Lucene.
- Extracts searchable text with Apache Tika (for example, from text documents and Tika-supported office/PDF formats).
- Sends search queries to every discovered peer and displays up to ten results per peer.
- Downloads a file from the peer selected by its exact filename, saving it under a user-chosen name.
- Provides a native Java Swing GUI; no browser, web server, or external database is involved.

## Project structure

```text
JAVA_LAN_ENGINE-main/
├── README.md
└── p2p-engine/
    ├── pom.xml                         # Maven coordinates, Java version, dependencies
    └── src/main/java/com/lansearch/
        ├── SearchApp.java               # Application entry point and Swing UI
        ├── PeerDiscovery.java           # UDP multicast peer discovery
        ├── FileIndexer.java             # Lucene/Tika indexing of shared files
        ├── FileSearcher.java            # Local Lucene search service
        ├── FileServer.java              # TCP server for search and download requests
        └── FileClient.java              # TCP download client
```

At runtime, the application also creates these directories in the current user's home directory:

```text
~/P2P_Shared/    # Files offered to peers and location for downloaded files
~/P2P_Index/     # On-disk Apache Lucene index
```

`P2P_Shared` is flat: only regular files directly inside that folder are indexed. Subdirectories are not traversed.

## Technology stack

| Area | Technology | Purpose |
| --- | --- | --- |
| Language/runtime | Java 21 | Project source and target level configured in Maven |
| Build tool | Apache Maven | Dependency management and compilation |
| Desktop UI | Java Swing/AWT | Search field, buttons, dialogs, and results pane |
| Full-text search | Apache Lucene 9.10.0 | Persistent index and filename/content search |
| Text extraction | Apache Tika 2.9.2 | Extracts text from supported document types before indexing |
| Discovery | UDP multicast | Announces and identifies LAN peers |
| Requests/transfers | TCP sockets | Sends searches and streams file bytes |
| File-change detection | Java `WatchService` | Reindexes after share-folder changes |
| Concurrency | Java threads and concurrent collections | Runs UI, server, discovery, file watching, and requests without blocking each other |

## Maven coordinates and dependencies

| Property | Value |
| --- | --- |
| Group ID | `com.lansearch` |
| Artifact ID | `p2p-engine` |
| Version | `1.0-SNAPSHOT` |
| Source/target | Java 21 |
| Encoding | UTF-8 |

Declared libraries:

- `org.apache.lucene:lucene-core:9.10.0`
- `org.apache.lucene:lucene-queryparser:9.10.0`
- `org.apache.lucene:lucene-analysis-common:9.10.0`
- `org.apache.tika:tika-core:2.9.2`
- `org.apache.tika:tika-parsers-standard-package:2.9.2`

## Architecture and data flow

```text
                     UDP multicast: 230.0.0.0:4446, every 3 seconds
  ┌───────────┐  ───────────────────────────────────────────────────►  ┌───────────┐
  │ Device A  │  ◄───────────────────────────────────────────────────  │ Device B  │
  └─────┬─────┘                 peer IPs are remembered                 └─────┬─────┘
        │                                                                      │
        │ TCP port 8888: SEARCH:<query> / DOWNLOAD:<filename>                 │
        └──────────────────────────────────────────────────────────────────────┘

Each device: ~/P2P_Shared → Tika text extraction → Lucene index in ~/P2P_Index
```

On startup, `SearchApp` creates the two runtime directories, starts the TCP server, starts the multicast listener and broadcaster, then opens the Swing window. A background folder watcher observes create, delete, and modify events in `P2P_Shared`; each non-hidden change triggers a complete index rebuild.

## Components

### `SearchApp`

The entry point (`com.lansearch.SearchApp`) and UI coordinator.

- Creates `~/P2P_Shared` and `~/P2P_Index` if absent.
- Starts `FileServer` and `PeerDiscovery` before showing the window.
- Shows a 750×450 Swing `JFrame` titled **LAN P2P Search**.
- Provides **Search Network**, **Download File**, and **Rebuild Index** buttons.
- Keeps an in-memory, thread-safe `filename → peer IP` map from the most recent search. The map is cleared before each new search.
- Uses TCP to query every discovered peer in the background.
- Starts the share-folder watcher after the window is created.

### `PeerDiscovery`

- Listens on UDP port `4446` after joining multicast group `230.0.0.0`.
- Broadcasts the literal presence message `HELLO_LAN_SEARCH` to that group every 3 seconds.
- Adds each sender IP address to a `CopyOnWriteArraySet`, so duplicate advertisements do not create duplicate peers.
- Exposes the active peer IP set to `SearchApp`.

### `FileIndexer`

- Uses `StandardAnalyzer` and a Lucene `FSDirectory` at `~/P2P_Index`.
- Deletes the previous index and rebuilds it from scratch on every indexing operation.
- Protects rebuilds with a process-wide lock so the watcher and manual button cannot index simultaneously.
- Indexes direct regular files in `~/P2P_Shared`.
- Stores tokenized `filename` and non-tokenized absolute `path` fields.
- Attempts Tika `parseToString` extraction and indexes extracted text in an unstored `content` field. If extraction fails, the file is still indexed by filename and path.

### `FileSearcher`

- Opens the on-disk Lucene index and searches both `content` and `filename` through `MultiFieldQueryParser` with `StandardAnalyzer`.
- Parses the entered text as Lucene query syntax.
- Returns up to 10 matching filenames, ordered by Lucene relevance score.

### `FileServer`

- Starts a background `ServerSocket` on TCP port `8888`.
- Gives every connection its own background handler thread.
- Accepts `SEARCH` and `DOWNLOAD` requests described below.

### `FileClient`

- Opens a TCP connection to a peer on port `8888`.
- Requests a file and streams all received bytes into `~/P2P_Shared/<save-as name>`.

## Network protocol

The protocol is plain text for commands and newline-separated search responses. File data is sent as raw bytes until the server closes the connection.

| Operation | Client request | Server response |
| --- | --- | --- |
| Presence | UDP datagram `HELLO_LAN_SEARCH` to `230.0.0.0:4446` | Receiving peers remember the sender’s IP |
| Search | TCP `SEARCH:<query>\n` to port `8888` | Zero or more matching filenames, one per line, then connection close |
| Download | TCP `DOWNLOAD:<filename>\n` to port `8888` | Requested file’s raw bytes, then connection close |

No authentication, encryption, checksums, length framing, or error response is implemented by the protocol.

## Prerequisites

- JDK 21 or newer (the Maven compiler source and target are set to `21`).
- Apache Maven 3.6+.
- Two or more devices connected to the same local network for peer-to-peer use.
- A network that permits UDP multicast and inbound TCP connections to port `8888`.

Check your setup:

```bash
java -version
mvn -version
```

Example installation commands:

```bash
# macOS (Homebrew)
brew install openjdk@21 maven

# Windows (PowerShell / winget)
winget install Microsoft.OpenJDK.21
winget install Apache.Maven
```

On macOS, ensure the JDK is selected, for example by setting `JAVA_HOME` in your shell profile as appropriate for your installation.

## Build and run

From the repository root:

```bash
cd p2p-engine
mvn clean package
```

This compiles the project and creates `target/p2p-engine-1.0-SNAPSHOT.jar`. The current Maven configuration does **not** define a `Main-Class` manifest or an executable/fat-JAR plugin, so that JAR is not directly runnable with `java -jar` on its own.

To run the application as currently configured, open the `p2p-engine` Maven project in an IDE (for example IntelliJ IDEA or Eclipse), allow Maven to resolve dependencies, and run:

```text
com.lansearch.SearchApp
```

Use JDK 21 as the project SDK. Maven also provides a standard compile check:

```bash
mvn clean compile
```

## Using the application

1. Run `SearchApp` on each device connected to the same LAN.
2. On each device, place files to share directly in `~/P2P_Shared`.
3. The folder watcher will rebuild the index after a detected change; use **Rebuild Index** if you want to force a rebuild immediately.
4. Wait a few seconds for multicast discovery. The UI reports if no peers have yet been found.
5. Enter a filename term, a content term, or a valid Lucene query in the search field and click **Search Network**.
6. The results area lists matching filenames with the peer IP that returned each result.
7. Click **Download File**, enter the exact filename shown by the search, then enter the desired saved filename.
8. The file is downloaded to your own `~/P2P_Shared` directory.

Downloads rely on the in-memory results of the latest search. Search again if the application says the file location is unknown.

## Search behavior

- Both `filename` and extracted `content` are searched.
- File names are tokenized, so a name such as `project-notes.pdf` can be found by terms such as `project` or `notes`.
- The query is handled by Lucene’s `MultiFieldQueryParser`, not as a literal string. Lucene syntax such as `report AND budget` may be used; reserved characters or malformed syntax can cause the search to fail.
- Content indexing depends on successful Apache Tika extraction. Binary or unsupported files may only be findable by filename.
- A peer returns at most 10 matches for each search request.

## Ports and firewall

Allow the following inbound network traffic on every device running the app:

| Protocol | Port / destination | Purpose |
| --- | --- | --- |
| UDP | `4446`, multicast group `230.0.0.0` | Peer advertisements and discovery |
| TCP | `8888` | Search requests and file downloads |

Example Windows PowerShell commands, run as Administrator:

```powershell
New-NetFirewallRule -DisplayName "P2P Discovery (UDP)" -Direction Inbound -LocalPort 4446 -Protocol UDP -Action Allow
New-NetFirewallRule -DisplayName "P2P Transfer (TCP)" -Direction Inbound -LocalPort 8888 -Protocol TCP -Action Allow
```

macOS may prompt for permission to accept incoming connections. On managed networks, multicast can be disabled by router or Wi-Fi isolation settings; in that case automatic discovery will not work.

## Important limitations and security notes

This code is a prototype for trusted local networks, not a secure production file-sharing system.

- All LAN participants that can reach TCP port `8888` can search indexed filenames/content and request files by name.
- Traffic is unencrypted and unauthenticated; never use it to share sensitive files on an untrusted network.
- The server uses a requested filename directly beneath `P2P_Shared` and does not validate path traversal sequences. Do not expose the service outside a trusted LAN.
- Downloaded filenames are user supplied and not sanitized. Use simple filenames without path components.
- The result-location map is keyed only by filename. If different peers have a file with the same name, the latest matching peer overwrites the earlier association.
- Peer entries are never expired. A disconnected device can remain in the active peer set until the app is restarted.
- The peer discovery listener records every sender to the multicast group; it does not validate the UDP message content or exclude the local host.
- Indexing is a whole-folder rebuild, not incremental. Large shares or expensive Tika parsing can take time.
- Only the immediate contents of `P2P_Shared` are indexed; nested folders are ignored.
- File transfers have no file-size metadata, checksum, resume support, progress reporting, or server-side “not found” response body.
- UI text updates occur from some worker threads, which is not fully Swing event-dispatch-thread safe.
- The current build does not include automated tests, logging configuration, a license file, CI workflow, or a packaged executable distribution.

## Configuration reference

These values are currently hard-coded in the source:

| Setting | Value | Source |
| --- | --- | --- |
| Shared folder | `~/P2P_Shared` | `FileIndexer.DATA_DIR` |
| Lucene index folder | `~/P2P_Index` | `FileIndexer.INDEX_DIR` |
| Discovery multicast group | `230.0.0.0` | `PeerDiscovery.MULTICAST_GROUP` |
| Discovery UDP port | `4446` | `PeerDiscovery.PORT` |
| Advertisement interval | 3 seconds | `PeerDiscovery.broadcastPresence()` |
| TCP server port | `8888` | `FileServer` / `SearchApp` / `FileClient` |
| Maximum search results | 10 per peer | `FileSearcher.search()` |
| Transfer buffer | 8192 bytes | `FileServer` and `FileClient` |

To change these values, update the corresponding Java source and rebuild the Maven project.

## Troubleshooting

| Symptom | Likely cause and action |
| --- | --- |
| “No peers discovered yet” | Wait at least a few seconds, confirm both apps use the same LAN, and check UDP multicast/firewall rules. |
| Search returns no expected files | Verify the file is directly inside `~/P2P_Shared`, rebuild the index, and confirm Tika can extract its content if searching text inside it. |
| Search reports a failed connection | Check that the target device is running the app and that inbound TCP port `8888` is allowed. |
| Download says location is unknown | Search for the file first in the current session, then enter the exact result filename. |
| Download creates an unusable file | Ensure the requested file exists on the peer and use a safe, simple save name; the protocol has no error framing or integrity validation. |
| Maven compiler error about release 21 | Install/select JDK 21+ or change the Maven compiler properties in `pom.xml` deliberately. |

## License and contribution status

No license, contribution guide, issue template, test suite, or CI configuration is present in this repository. Treat usage and redistribution terms as unspecified until a license is added.
# P2P_LAN_File-Search
