#include "esp_camera.h"

// Official Seeed Studio XIAO ESP32-S3 Sense Camera Pin Definition Matrix
#define PWDN_GPIO_NUM     -1
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM     10
#define SIOD_GPIO_NUM     40
#define SIOC_GPIO_NUM     39

#define Y9_GPIO_NUM       48
#define Y8_GPIO_NUM       11
#define Y7_GPIO_NUM       12
#define Y6_GPIO_NUM       14
#define Y5_GPIO_NUM       16
#define Y4_GPIO_NUM       18
#define Y3_GPIO_NUM       17
#define Y2_GPIO_NUM       15
#define VSYNC_GPIO_NUM    38
#define HREF_GPIO_NUM     47
#define PCLK_GPIO_NUM     13

esp_err_t camera_init_result = ESP_OK;

void setup() {
    Serial.begin(921600);
    
    // Low-level hardware power-up delay to stabilize voltage rails
    delay(500);

    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sccb_sda = SIOD_GPIO_NUM;
    config.pin_sccb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    
    config.xclk_freq_hz = 10000000;
    config.frame_size = FRAMESIZE_SVGA;
    config.pixel_format = PIXFORMAT_JPEG;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.fb_count = 2;
    config.jpeg_quality = 7;

    // Initialize the camera hardware layer and store status code
    camera_init_result = esp_camera_init(&config);
    if (camera_init_result == ESP_OK) {
        sensor_t * s = esp_camera_sensor_get();
        if (s) {
            s->set_brightness(s, 1);
            s->set_contrast(s, 1);
        }
    }
}

void loop() {
    if (Serial.available() > 0) {
        char cmd = Serial.read();
        if (cmd == 'S') {
            // Check if the initialization phase encountered a system failure
            if (camera_init_result != ESP_OK) {
                Serial.print("ERR:INIT_FAIL_CODE_");
                Serial.println(camera_init_result);
                Serial.flush();
                return;
            }
            

            camera_fb_t * flush_fb1 = esp_camera_fb_get();
            if (flush_fb1) esp_camera_fb_return(flush_fb1);
            
            camera_fb_t * flush_fb2 = esp_camera_fb_get();
            if (flush_fb2) esp_camera_fb_return(flush_fb2);

            delay(50);

            // Direct Frame Capture Pipeline
            camera_fb_t * fb = esp_camera_fb_get();
            if (!fb) {
                Serial.println("ERR:FRAME_BUFFER_NULL");
                Serial.flush();
                return;
            }
            
            // Raw binary streaming over USB interface
            Serial.write(fb->buf, fb->len);
            Serial.flush();
            
            // Release memory block back to driver queue
            esp_camera_fb_return(fb);
        }
    }
}