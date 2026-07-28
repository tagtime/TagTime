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
// Build:  swiftc -O -o TagTime tagtime-panel.swift
//         (binary named TagTime: the cmd-tab switcher labels an unbundled
//         app with its executable name -- LaunchServices ignores an embedded
//         __info_plist's CFBundleName, so renaming is the whole mechanism)
// Usage:  TagTime /path/to/ping.pl <pingtime>
// Exits with the child's exit status; closing the window without answering
// terminates the child and exits 1 (launch.pl then logs "err" as ever).

import AppKit

let args = Array(CommandLine.arguments.dropFirst())
if args.isEmpty {
  FileHandle.standardError.write("usage: TagTime cmd [args...]\n".data(using: .utf8)!)
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
// .regular, not .accessory: regular apps are listed in the cmd-tab switcher,
// and cmd-tab is user-initiated activation -- the one signal macOS 26 always
// honors -- so it's a reliable keyboard-recovery path when the initial grab
// was deferred. Costs a generic Dock icon while a ping is up.
app.setActivationPolicy(.regular)

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

// Dock and cmd-tab switcher icon: a plain red square matching the panel,
// drawn at runtime so the unbundled binary needs no icon asset.
app.applicationIconImage = NSImage(size: NSSize(width: 512, height: 512), flipped: false) { r in
  bgRed.setFill()
  r.fill()
  return true
}

// Show on the screen holding the mouse pointer -- that's where attention is.
let mouse = NSEvent.mouseLocation
let screen = NSScreen.screens.first { NSMouseInRect(mouse, $0.frame, false) } ?? NSScreen.main!
// wide enough for ping.pl's 80-col annotime lines; short enough that the
// typical 4-line transcript doesn't leave a vast red void above the input
let W: CGFloat = 700, H: CGFloat = 300
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
// red bleeds into the title bar; dark appearance keeps the title legible on it
panel.backgroundColor = bgRed
panel.titlebarAppearsTransparent = true
panel.appearance = NSAppearance(named: .darkAqua)
panel.isMovableByWindowBackground = true
// minimize/zoom are disabled by the styleMask; hide their dead gray dots
panel.standardWindowButton(.miniaturizeButton)?.isHidden = true
panel.standardWindowButton(.zoomButton)?.isHidden = true

let pad: CGFloat = 10
let content = panel.contentView!

// The input field is the one white thing on the red window, so it reads
// unmistakably as the place to type. No placeholder: the transcript's prompt
// line already asks the question. Same font as the transcript so typed text
// doesn't change size when it echoes there. Sized to its natural height --
// an NSTextField taller than that top-aligns its text while editing.
let input = ClickField(frame: .zero)
input.font = mono
input.appearance = NSAppearance(named: .aqua)  // stay white despite the darkAqua window
input.textColor = .black
input.sizeToFit()
let barH = input.frame.height + 2*pad
input.frame = NSRect(x: pad, y: pad, width: W - 2*pad, height: input.frame.height)
input.autoresizingMask = [.width, .maxYMargin]

// no scroller: it's a transcript, auto-pinned to the end (trackpad still scrolls)
let scroll = NSScrollView(frame: NSRect(x: 0, y: barH, width: W, height: content.bounds.height - barH))
scroll.autoresizingMask = [.width, .height]
scroll.hasVerticalScroller = false
scroll.drawsBackground = false
// A plain click on the transcript bounces focus back to the input field,
// caret after any half-typed answer (makeFirstResponder alone would
// select-all, letting the next keystroke wipe the answer). A drag or
// double-click makes a selection, which keeps focus here so cmd-C works.
// So does any click once the answer is accepted: a disabled field refuses
// first-responderhood, making the bounce a no-op then.
class Transcript: NSTextView {
  override func mouseDown(with event: NSEvent) {
    super.mouseDown(with: event)  // the whole click-or-drag selection loop
    if selectedRange.length == 0 {
      window?.makeFirstResponder(input)
      input.currentEditor()?.selectedRange =
        NSRange(location: (input.stringValue as NSString).length, length: 0)
    }
  }
}
let tv = Transcript(frame: NSRect(origin: .zero, size: scroll.contentSize))
tv.autoresizingMask = [.width]
tv.isEditable = false
// canonical grow-with-content setup so a long transcript (task list,
// beeminder updates) keeps scrolling instead of clipping at the frame height
tv.isVerticallyResizable = true
tv.maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
tv.textContainer?.widthTracksTextView = true
tv.backgroundColor = bgRed
tv.textContainerInset = NSSize(width: pad, height: pad)
scroll.documentView = tv
content.addSubview(scroll)
content.addSubview(input)

// raw text only: smart-quote substitution would turn the ditto response (a
// lone ") into a curly quote ping.pl doesn't recognize
if let fe = panel.fieldEditor(true, for: input) as? NSTextView {
  fe.insertionPointColor = bgRed  // the default hairline black caret is easy to lose
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

// Carryover for a title escape split across pipe reads. Main thread only.
var escBuf = ""

// Pull xterm title escapes (ESC ] 0-or-2 ; title BEL; ping.pl emits them
// when further pings ping) out of a chunk of child output: apply each as
// the panel title and return the chunk with them removed. An incomplete
// sequence at the end of a chunk waits in escBuf for the next chunk --
// bounded, so a stray ESC ] can't swallow the transcript forever.
func consume(_ chunk: String) -> String {
  var buf = escBuf + chunk
  escBuf = ""
  var display = ""
  while let esc = buf.range(of: "\u{1b}]") {
    display += String(buf[..<esc.lowerBound])
    let rest = String(buf[esc.lowerBound...])
    guard let bel = rest.range(of: "\u{07}") else {
      if rest.count < 512 { escBuf = rest; return display }
      return display + rest
    }
    let body = rest[rest.index(rest.startIndex, offsetBy: 2)..<bel.lowerBound]
    if body.hasPrefix("0;") || body.hasPrefix("2;") {
      panel.title = String(body.dropFirst(2))
    } else {
      display += String(rest[..<bel.lowerBound])  // some other OSC; show it
    }
    buf = String(rest[bel.upperBound...])
  }
  return display + buf
}

outPipe.fileHandleForReading.readabilityHandler = { fh in
  let data = fh.availableData
  if data.isEmpty { return }
  // lossy decode: a multibyte char split across two pipe reads must garble
  // one char, not drop the chunk
  let raw = String(decoding: data, as: UTF8.self)
  DispatchQueue.main.async {
    let s = consume(raw)
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
  // User-initiated focus of the panel (a click anywhere on it, cmd-tab, the
  // Dock icon) lands the caret in the input field while the ping awaits an
  // answer. Not a re-grab: this runs only once the user has already handed
  // us the keyboard. Skipped when an editing session is live -- refocusing
  // the field would select-all and a keystroke would then wipe a
  // half-typed answer.
  func windowDidBecomeKey(_ n: Notification) {
    if input.isEnabled && input.currentEditor() == nil {
      (n.object as! NSWindow).makeFirstResponder(input)
    }
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

// Cmd-tab activation reaches the app but doesn't automatically key a panel
// (panels can't be main windows), so key it ourselves; windowDidBecomeKey
// then lands the caret in the field.
NotificationCenter.default.addObserver(forName: NSApplication.didBecomeActiveNotification,
                                       object: nil, queue: .main) { _ in
  panel.makeKeyAndOrderFront(nil)
}

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
