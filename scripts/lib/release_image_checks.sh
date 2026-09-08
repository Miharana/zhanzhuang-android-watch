# Shared image-format checks for the Google Play release verifier and its contract tests.
# This file is sourced; callers must provide fail().

image_size() { sips -g pixelWidth -g pixelHeight "$1" 2>/dev/null | awk '/pixelWidth:/{w=$2} /pixelHeight:/{h=$2} END {if (w && h) print w "x" h}'; }
image_has_alpha() { sips -g hasAlpha "$1" 2>/dev/null | awk '/hasAlpha:/{print $2}'; }
image_property() { sips -g "$2" "$1" 2>/dev/null | awk -v property="$2" '$1 == property ":" { print $2; exit }'; }

verify_play_icon() {
    local icon=$1 format bits samples alpha
    [[ $(image_size "$icon") == '512x512' ]] || fail 'Play icon must be 512x512'
    format=$(image_property "$icon" format)
    bits=$(image_property "$icon" bitsPerSample)
    samples=$(image_property "$icon" samplesPerPixel)
    alpha=$(image_has_alpha "$icon")
    [[ "$format" == png && "$bits" == 8 && "$samples" == 4 && "$alpha" == yes ]] || fail 'Play icon must be 8-bit RGBA/32-bit PNG with alpha'
    [[ $(stat -f '%z' "$icon") -le 1048576 ]] || fail 'Play icon exceeds 1 MiB'
}

verify_wear_screenshot() {
    local screenshot=$1 dimensions width height
    dimensions=$(image_size "$screenshot"); width=${dimensions%x*}; height=${dimensions#*x}
    [[ "$width" == "$height" && "$width" -ge 384 && "$width" -le 3840 ]] || fail 'Wear screenshot must be square and between 384px and 3840px'
    [[ $(image_has_alpha "$screenshot") == 'no' ]] || fail 'Wear screenshot must not have alpha'
}
