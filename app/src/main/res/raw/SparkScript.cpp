// Define the pins we're going to call pinMode on
int led2 = D7; // This one is the built-in tiny one to the right of the USB jack

#define MAC_BYTES 6
#define REPEAT_MAC 16
#define MAGIC_HEADER_LENGTH 6

uint16_t port = 7;
IPAddress broadcastIP(255,255,255,255);
IPAddress pingIP;
char szState[32];

#define NOT_CONNECTED 0
#define WAITING 1
#define SENDING_WOL 2
#define WOL_SENT 3
#define TESTING_AWAKE 4
#define TESTING_AWAKE_2 5
#define TESTING_AWAKE_3 6
#define FAILED_TO_WAKE_WAITING 7
#define CONFIRMED_AWAKE_WAITING 8

int state = NOT_CONNECTED;

uint8_t hex_to_byte(uint8_t h, uint8_t l) {
    uint8_t retval = 0x00;

    // higher nibble
    if (h >= 0x30 && h <= 0x39) { // 0-9
        retval |= (h - 0x30) << 4;
    }

    if (h >= 0x41 && h <= 0x46) { // A-F
        retval |= (h - 0x41 + 0x0A) << 4;
    }

    if (h >= 0x61 && h <= 0x66) { // a-f
        retval |= (h - 0x61 + 0x0A) << 4;
    }

    // lower nibble
    if (l >= 0x30 && l <= 0x39) { // 0-9
        retval |= l - 0x30;
    }

    if (l >= 0x41 && l <= 0x46) { // A-F
        retval |= l - 0x41 + 0x0A;
    }

    if (l >= 0x61 && l <= 0x66) { // a-f
        retval |= l - 0x61 + 0x0A;
    }

    return retval;
}

void parseMacAddress(const char* string, uint8_t* target) {
    uint8_t i = 0;
    uint8_t j = 0;
    uint8_t max = 17; // MAC String is 17 characters.
    while (i < max) {
        target[j++] = hex_to_byte(string[i], string[i + 1]);
        i += 3;
    }
}

bool parseIPAddress(String string, IPAddress* target) {
    uint8_t values[4] = { 0, 0, 0, 0 };
    int prevIndex = -1;
    for (int i = 0; i < 4; ++i)
    {
        int dotIndex = string.indexOf('.', prevIndex + 1);
        if (dotIndex < 0)
        {
            if (i != 3)
            {
                // ERROR
                return false;
            }

            dotIndex = string.length();
        }

        values[i] = string.substring(prevIndex + 1, dotIndex).toInt();
        prevIndex = dotIndex;
    }

    *target = IPAddress(values);

    return true;
}

int wake(const char* mac) {
    uint8_t contents[MAGIC_HEADER_LENGTH + REPEAT_MAC * MAC_BYTES];
    uint8_t rawMac[MAC_BYTES];

    state = SENDING_WOL;

    parseMacAddress(mac, rawMac);

    UDP udp;
    udp.begin(port);
    udp.beginPacket(broadcastIP, port);

    for (int i = 0; i < MAGIC_HEADER_LENGTH; i++) {
        contents[i] = 0xFF;
    }
    for (uint8_t i = MAGIC_HEADER_LENGTH; i < sizeof contents; i++) {
        contents[i] = rawMac[(i - MAGIC_HEADER_LENGTH) % MAC_BYTES];
    }

    Serial.write("Packet:");
    Serial.write(contents, sizeof contents);
    udp.write(contents, sizeof contents);

    udp.endPacket();
    udp.stop();

    state = WOL_SENT;

    return TRUE;
}
int wakeHost(String param) {
    if (param.length() == 0)
    {
        strcpy(szState, "Invalid arguments");
        return FALSE;
    }

    int index = param.indexOf(';');
    if (index == -1 || param.indexOf(';', index + 1) >= 0 || !parseIPAddress(param.substring(0, index), &pingIP))
    {
        strcpy(szState, "Invalid arguments");
        return FALSE;
    }

    char szMacAddress[80];
    param.substring(index + 1).toCharArray(szMacAddress, 80);
    return wake(szMacAddress);
}

void setup() {
    pinMode(led2, OUTPUT);

    strcpy(szState, "");
    Spark.variable("state", &szState, STRING);

    Spark.function("wakeHost", wakeHost);

    Serial.begin(9600);

    state = WAITING;

    RGB.control(true); // take control of the LED
    RGB.brightness(20); // Lower LED intensity
}

void loop() {
    switch (state)
    {
        case WOL_SENT:
            strcpy(szState, "Sent WOL");
            digitalWrite(led2, HIGH);
            delay(250);               // Wait for 250mS
            digitalWrite(led2, LOW);
            delay(250);               // Wait for 250mS
            digitalWrite(led2, HIGH);
            delay(250);               // Wait for 250mS
            digitalWrite(led2, LOW);
            delay(1000);              // Wait for 1 second

            state = TESTING_AWAKE;
            break;
        case TESTING_AWAKE_2:
        case TESTING_AWAKE_3:
            delay(1000);              // Wait for 1 second
        case TESTING_AWAKE:
        {
            strcpy(szState, "Pinging");
            digitalWrite(led2, HIGH);
            delay(250);               // Wait for 250mS
            digitalWrite(led2, LOW);

            if (WiFi.ping(pingIP) > 0)
                state = CONFIRMED_AWAKE_WAITING;
            else
                ++state;
            break;
        }
        case CONFIRMED_AWAKE_WAITING:
            strcpy(szState, "Reachable");
            RGB.color(0, 255, 0);
            delay(1000);               // Wait for 1 seconds
            RGB.color(0, 0, 0);
            delay(500);                // Wait for 500mS
            RGB.color(0, 255, 0);
            delay(1000);               // Wait for 1 seconds
            RGB.color(0, 0, 0);        // Disable LED
            state = WAITING;
            break;
        case FAILED_TO_WAKE_WAITING:
            strcpy(szState, "Unreachable");
            RGB.color(255, 0, 0);
            delay(2000);               // Wait for 2 seconds
            RGB.color(0, 0, 0);        // Disable LED
            state = WAITING;
            break;
    }
}
