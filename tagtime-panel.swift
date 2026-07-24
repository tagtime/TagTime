// TagTime ping panel: pop up an unmissable red window and run the given
// command (ping.pl) in it, streaming its output and feeding it typed input.
//
// Why this exists: macOS 26's focus-stealing prevention defers the visual
// raise of any window whose raise was requested by a background process
// (tagtimed), releasing it only on user-initiated app activation -- so
// AppleScript-driven iTerm2/Terminal popups sat invisible until an alt-tab.
// An NSPanel at status-bar window level shown via orderFrontRegardless is
// visible unconditionally: on every Space, over fullscreen apps, above
// normal windows, no raise request for macOS to defer. It tries to take the
// keyboard once at popup (classic TagTime interrupt; macOS may defer that
// grant until you click) and never re-takes it.
//
// Build:  swiftc -O -o tagtime-panel tagtime-panel.swift
// Usage:  tagtime-panel /path/to/ping.pl <pingtime>
// Exits with the child's exit status; closing the window without answering
// terminates the child and exits 1 (launch.pl then logs "err" as ever).

import AppKit

let args = Array(CommandLine.arguments.dropFirst())
if args.isEmpty {
  FileHandle.standardError.write("usage: tagtime-panel cmd [args...]\n".data(using: .utf8)!)
  exit(2)
}

// Temporary instrumentation shared with the popup scripts: hi-res timestamped
// lines in tmp/popup-timing.log next to this binary. Remove when diagnosed.
let binDir = (CommandLine.arguments[0] as NSString).deletingLastPathComponent
func tslog(_ msg: String) {
  let path = binDir + "/tmp/popup-timing.log"
  let line = String(format: "%.3f %@\n", Date().timeIntervalSince1970, msg)
  if !FileManager.default.fileExists(atPath: path) {
    FileManager.default.createFile(atPath: path, contents: nil)
  }
  guard let fh = FileHandle(forWritingAtPath: path) else {
    FileHandle.standardError.write("SYSERR: can't append to popup-timing.log\n".data(using: .utf8)!)
    return
  }
  fh.seekToEndOfFile()
  fh.write(line.data(using: .utf8)!)
  fh.closeFile()
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)

class KeyablePanel: NSPanel {
  override var canBecomeKey: Bool { true }
}

// let the very first click in the field register (no click-to-focus-window,
// then click-again-to-focus-field dance)
class ClickField: NSTextField {
  override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }
}

let bgRed = NSColor(calibratedRed: 0.87, green: 0.08, blue: 0.08, alpha: 1.0)
let mono = NSFont.monospacedSystemFont(ofSize: 13, weight: .regular)

// Show on the screen holding the mouse pointer -- that's where attention is.
let mouse = NSEvent.mouseLocation
let screen = NSScreen.screens.first { NSMouseInRect(mouse, $0.frame, false) } ?? NSScreen.main!
let W: CGFloat = 700, H: CGFloat = 420
let vis = screen.visibleFrame
let rect = NSRect(x: vis.midX - W/2, y: vis.midY - H/2, width: W, height: H)

// NOT .nonactivatingPanel: on macOS 26 a never-activating app never
// qualifies for keyboard focus at all (mouse reached the panel; keystrokes
// kept routing to the active app). Activating on click is the one
// user-initiated signal macOS always honors.
let panel = KeyablePanel(contentRect: rect,
                         styleMask: [.titled, .closable],
                         backing: .buffered, defer: false)
panel.title = "TagTime"
// no isFloatingPanel: its setter would clobber this explicit level with the
// lower .floating
panel.level = .statusBar
panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
// panels default to hiding when the app deactivates, which for this app
// (active only while you're typing into it) would mean vanishing
panel.hidesOnDeactivate = false

let inputH: CGFloat = 28
let content = panel.contentView!

let scroll = NSScrollView(frame: NSRect(x: 0, y: inputH, width: W, height: content.bounds.height - inputH))
scroll.autoresizingMask = [.width, .height]
scroll.hasVerticalScroller = true
let tv = NSTextView(frame: NSRect(origin: .zero, size: scroll.contentSize))
tv.autoresizingMask = [.width]
tv.isEditable = false
tv.backgroundColor = bgRed
tv.textContainerInset = NSSize(width: 6, height: 6)
scroll.documentView = tv
content.addSubview(scroll)

let input = ClickField(frame: NSRect(x: 0, y: 0, width: W, height: inputH))
input.autoresizingMask = [.width, .maxYMargin]
input.font = mono
input.placeholderString = "What are you doing RIGHT NOW?"
content.addSubview(input)

// raw text only: smart-quote substitution would turn the ditto response (a
// lone ") into a curly quote ping.pl doesn't recognize
if let fe = panel.fieldEditor(true, for: input) as? NSTextView {
  fe.isAutomaticQuoteSubstitutionEnabled = false
  fe.isAutomaticDashSubstitutionEnabled = false
  fe.isAutomaticTextReplacementEnabled = false
  fe.isAutomaticSpellingCorrectionEnabled = false
}

func append(_ s: String) {
  // strip ANSI color escapes; ping.pl emits them when Term::ANSIColor loads
  let clean = s.replacingOccurrences(of: "\u{1b}\\[[0-9;]*m", with: "", options: .regularExpression)
  tv.textStorage?.append(NSAttributedString(string: clean, attributes: [.font: mono, .foregroundColor: NSColor.white]))
  tv.scrollToEndOfDocument(nil)
}

let proc = Process()
proc.executableURL = URL(fileURLWithPath: args[0])
proc.arguments = Array(args.dropFirst())
let inPipe = Pipe(), outPipe = Pipe()
proc.standardInput = inPipe
proc.standardOutput = outPipe
proc.standardError = outPipe
var finished = false

// the ping timestamp argument, for recognizing ping.pl's acceptance echo
let pingArg: String? = args.count >= 2 ? args[1] : nil

outPipe.fileHandleForReading.readabilityHandler = { fh in
  let data = fh.availableData
  if data.isEmpty { return }
  // lossy decode: a multibyte char split across two pipe reads must garble
  // one char, not drop the chunk
  let s = String(decoding: data, as: UTF8.self)
  DispatchQueue.main.async {
    append(s)
    // Once ping.pl accepts the answer (it echoes the log line, which starts
    // with the ping timestamp we passed it), nothing reads stdin anymore, so
    // gray out the input rather than swallow keystrokes. A rejected answer
    // (enforcenums/enforcenonon) echoes nothing and silently awaits a
    // retype, so the field must NOT lock at submit time.
    if let t = pingArg, s.split(separator: "\n").contains(where: { $0.hasPrefix(t + " ") }) {
      input.isEnabled = false
    }
    // ...except ping.pl's error prompt does read one more line; see the
    // "press enter to dismiss" prompt in ping.pl
    if s.contains("press enter to dismiss") {
      input.isEnabled = true
      panel.makeFirstResponder(input)
    }
  }
}

proc.terminationHandler = { p in
  DispatchQueue.main.async {
    finished = true
    tslog("xt-exit")
    outPipe.fileHandleForReading.readabilityHandler = nil
    exit(p.terminationStatus)
  }
}

class Actions: NSObject, NSWindowDelegate {
  @objc func submit(_ sender: NSTextField) {
    let line = sender.stringValue
    sender.stringValue = ""
    append(line + "\n")  // piped stdin isn't tty-echoed, so echo it ourselves
    inPipe.fileHandleForWriting.write((line + "\n").data(using: .utf8)!)
  }
  func windowWillClose(_ n: Notification) {
    if !finished {  // closed without answering: kill child, exit nonzero
      proc.terminate()
      exit(1)
    }
  }
}
let actions = Actions()
input.target = actions
input.action = #selector(Actions.submit(_:))
panel.delegate = actions

tslog("xt-start " + args.joined(separator: " "))
do {
  try proc.run()
} catch {
  FileHandle.standardError.write("SYSERR: can't run \(args[0]): \(error)\n".data(using: .utf8)!)
  exit(2)
}

func grabKeyboard() {
  NSApp.activate(ignoringOtherApps: true)
  panel.makeKeyAndOrderFront(nil)
  panel.makeFirstResponder(input)
}

panel.orderFrontRegardless()
grabKeyboard()  // keyboard captured once, at popup, only
// macOS 26 can defer key status granted to a background process, leaving the
// panel visible but not typeable, so re-grab a few times in the first
// moments. Initial capture only; never re-grabbed after that.
for delay in [0.3, 0.8, 1.5] {
  DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
    if !panel.isKeyWindow { grabKeyboard() }
  }
}

app.run()
