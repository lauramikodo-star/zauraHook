# Fake Calculator Feature

## Overview

The **Fake Calculator** feature provides an additional layer of privacy and security for cloned apps by disguising the app entrance behind a fully functional calculator interface. When enabled, launching the cloned app from the home screen will show a calculator instead of the actual app. Users must enter a secret passcode to access the real application.

## Features

### 1. **Functional Calculator Interface**
- Fully working calculator with basic arithmetic operations (+, -, ×, ÷, %)
- Professional dark-themed UI
- Supports decimal numbers
- Clear (C) and backspace (⌫) functionality
- Looks and behaves like a real calculator app

### 2. **Passcode Protection**
- Configurable passcode (default: `1234=`)
- Passcode entry is seamlessly integrated into calculator usage
- No visible password field - enter passcode using calculator buttons
- Example: To enter default passcode, press: `1` → `2` → `3` → `4` → `=`

### 3. **Stealth Mode**
- Calculator appears as the app icon on the home screen
- Prevents unauthorized access to sensitive apps
- No indication that this is not a real calculator
- Back button moves app to background instead of closing

## Configuration

### Enable Fake Calculator

In the cloning configuration (`cloner.json` or via the app cloner UI):

```json
{
  "fake_calculator_enabled": true,
  "fake_calculator_passcode": "1234="
}
```

### Settings Options

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `fake_calculator_enabled` | Boolean | `false` | Enable/disable the fake calculator entrance |
| `fake_calculator_passcode` | String | `"1234="` | Passcode to access the real app |

### Setting Passcode Examples

**Default passcode** (press these buttons in order):
```
1 → 2 → 3 → 4 → =
```

**Custom passcode** (e.g., "9876="):
```
9 → 8 → 7 → 6 → =
```

**Complex passcode** (e.g., "2580="):
```
2 → 5 → 8 → 0 → =
```

**Calculation-based passcode** (e.g., "111+222="):
```
1 → 1 → 1 → + → 2 → 2 → 2 → =
```

## User Experience Flow

### First Launch Sequence:

1. **User taps app icon** on home screen
2. **Calculator appears** instead of the actual app
3. **User performs calculations** (optional - calculator works normally)
4. **User enters passcode** using calculator buttons (e.g., `1234=`)
5. **Passcode verified** → Real app launches
6. **Calculator closes** automatically

### Subsequent Usage:

- After entering the correct passcode once, the app remains accessible
- Calculator will appear again when:
  - App is force-stopped
  - Device is restarted
  - App is closed and relaunched from home screen after significant time

## Technical Details

### Architecture

1. **FakeCalculatorActivity**
   - Implements a fully functional calculator UI
   - Programmatically created layout (no XML dependencies)
   - Tracks button inputs to detect passcode
   - Launches real app when correct passcode is entered

2. **FakeCalculatorHook**
   - Extends `ExecStartActivityHook`
   - Intercepts app launch intents
   - Redirects launcher intents to calculator
   - Manages passcode state and bypass logic

3. **Integration Points**
   - Registered in `hook/src/main/AndroidManifest.xml`
   - Initialized in `DefaultProvider.onCreate()`
   - Settings stored in `ClonerSettings` and SharedPreferences

### Code Structure

```
hook/src/main/java/com/applisto/appcloner/
├── FakeCalculatorActivity.java    # Calculator UI and passcode verification
├── FakeCalculatorHook.java        # Launch interception and hook logic
└── ClonerSettings.java            # Configuration loading
```

## API Reference

### FakeCalculatorActivity

#### Static Methods

```java
// Set custom passcode programmatically
public static void setPasscode(Context context, String passcode)

// Get current passcode
public static String getPasscode(Context context)
```

### FakeCalculatorHook

#### Static Methods

```java
// Enable or disable fake calculator
public static void setEnabled(Context context, boolean enabled)

// Set the passcode
public static void setPasscode(Context context, String passcode)

// Reset launch flag (force calculator to show on next launch)
public static void resetLaunchFlag(Context context)

// Check if feature is enabled
public static boolean isEnabled(Context context)

// Get current passcode
public static String getPasscode(Context context)
```

## Security Considerations

### Strengths
- **No visual indicators**: Calculator looks completely authentic
- **Flexible passcodes**: Can use any combination of numbers and operators
- **Functional disguise**: Calculator works normally, making it less suspicious
- **Back button protection**: Pressing back moves app to background (doesn't close)

### Limitations
- **Passcode stored locally**: Passcode is saved in SharedPreferences
- **No rate limiting**: No delay after incorrect attempts (by design - maintains calculator authenticity)
- **Memory persistence**: After correct passcode, app remains accessible until specific events

### Best Practices
1. **Choose unique passcodes**: Avoid obvious patterns (1234, 0000, etc.)
2. **Use operators**: Include `+`, `-`, `×`, `÷`, or `%` for added complexity
3. **Update regularly**: Change passcode periodically via cloner settings
4. **Test thoroughly**: Verify passcode works before relying on it

## Troubleshooting

### Calculator doesn't appear
- Verify `fake_calculator_enabled` is set to `true` in settings
- Check that `FakeCalculatorActivity` is registered in AndroidManifest.xml
- Ensure `FakeCalculatorHook` is initialized in DefaultProvider

### Passcode not working
- Confirm passcode ends with `=` (required for verification)
- Check passcode setting in SharedPreferences
- Try resetting passcode via cloner configuration
- Verify no whitespace or special characters (except calculator operators)

### App launches directly (bypassing calculator)
- Check if `fake_calculator_enabled` is `false`
- Verify hook installation in DefaultProvider
- Check if launch flag needs reset: `FakeCalculatorHook.resetLaunchFlag(context)`

## Examples

### Example 1: Basic Configuration

```json
{
  "fake_calculator_enabled": true,
  "fake_calculator_passcode": "2468="
}
```

User enters: `2` → `4` → `6` → `8` → `=`

### Example 2: Complex Passcode

```json
{
  "fake_calculator_enabled": true,
  "fake_calculator_passcode": "99×11="
}
```

User enters: `9` → `9` → `×` → `1` → `1` → `=`

### Example 3: Long Passcode

```json
{
  "fake_calculator_enabled": true,
  "fake_calculator_passcode": "123+456="
}
```

User enters: `1` → `2` → `3` → `+` → `4` → `5` → `6` → `=`

## Future Enhancements

Potential improvements for future versions:

1. **Biometric authentication**: Add fingerprint/face unlock option
2. **Decoy functionality**: Configure calculator to show fake app data on wrong passcode
3. **Custom themes**: Allow different calculator color schemes
4. **Advanced mode**: Multiple passcodes for different users
5. **Time-based access**: Restrict access to specific time windows
6. **Panic button**: Quick button combination to lock app
7. **Usage analytics**: Track failed access attempts (optional)

## Related Features

- **Fake Camera**: Similar privacy feature for camera operations
- **App Name & Icon**: Customize how the app appears on device
- **Floating Window**: Additional access method via floating window

## Support

For issues or questions:
1. Check this documentation
2. Review code comments in source files
3. Test with default passcode (`1234=`) first
4. Verify AndroidManifest registration

---

**Version**: 1.0  
**Last Updated**: 2025-11-29  
**Compatibility**: Android 5.0+ (API 21+)
