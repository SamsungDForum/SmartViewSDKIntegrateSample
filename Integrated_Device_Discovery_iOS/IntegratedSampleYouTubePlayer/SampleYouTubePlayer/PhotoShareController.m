/*
 
 Copyright (c) 2014 Samsung Electronics
 
 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:
 
 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.
 
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 
 */

#import "PhotoShareController.h"

@import AssetsLibrary;

@implementation PhotoShareController

+ (instancetype)sharedInstance
{
    static PhotoShareController *controller;
    static dispatch_once_t token;
    dispatch_once(&token, ^{
        controller = [self new];
    });
    return controller;
}

-(instancetype)init
{
    self = [super init];
    self.appId = @"0rLFmRVi9d.youtubetest";
    self.channelId = @"com.samsung.msf.youtubetest";
    self.services = [NSMutableArray arrayWithCapacity:0];
    self.search = [Service search];
    self.search.delegate = self;
    
    if(!kReceiverAppID)
    {
        kReceiverAppID = [[NSString alloc] initWithString:kGCKMediaDefaultReceiverApplicationID];
    }
    
    GCKFilterCriteria* filter = [GCKFilterCriteria criteriaForAvailableApplicationWithID:kReceiverAppID];
    
    self.deviceScanner = [[GCKDeviceScanner alloc] initWithFilterCriteria:filter];
   
    return self;
}

-(void) searchServices
{
    [self.search start];
    [self updateCastStatus];
}

-(void) scanServices
{
    [self.deviceScanner addListener:self];
    [self.deviceScanner startScan];
    [self.deviceScanner setPassiveScan:YES];
}

-(void) connect:(Service *) service
{
    self.app = [service createApplication:self.appId channelURI:self.channelId args:nil];
    self.app.delegate = self;
    self.app.connectionTimeout = 5;
    self.isConnecting = YES;
    [self updateCastStatus];
    [self.app connect:@{@"name": [UIDevice currentDevice].name}];
}


-(CastStatus) getCastStatus
{
    CastStatus castStatus = notReady;
    if ((self.app != nil && self.app.isConnected) || (self.deviceManager != nil && (self.deviceManager.connectionState == GCKConnectionStateConnected)))
    {
        castStatus = connected;
    }
    else if (self.isConnecting)
    {
        castStatus = connecting;
    }
    else if (self.services.count > 0 || self.deviceScanner.devices.count > 0)
    {
        castStatus = readyToConnect;
    }
    return castStatus;
}

-(void) castImage:(NSURL*) imageURL
{
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        ALAssetsLibrary * assetLib = [[ALAssetsLibrary alloc] init];
        
        [assetLib assetForURL:imageURL resultBlock:^(ALAsset *asset) {
            if (asset != nil)
            {
                ALAssetRepresentation *representation = [asset defaultRepresentation];
                
                UIImage * image = [UIImage imageWithCGImage:[representation fullResolutionImage]];
                NSData *imageData = UIImageJPEGRepresentation(image, 0.6);
                [[PhotoShareController sharedInstance].app publishWithEvent:@"showPhoto" message:nil data:imageData target:@"all"];
            }
        } failureBlock:^(NSError *error) {
            NSLog(@"Error");
        }];
    });
}

// MARK: Private Methods
-(void) updateCastStatus
{
    // Update the cast button status: Since there may be many cast buttons and
    // the PhotoShareController does not need to be coupled to the view controllers
    // the use of Notifications seems appropriate.
    CastStatus castStatus = [self getCastStatus];
    
    NSString * castStatusString = @"notReady";
    if (castStatus == notReady)
        castStatusString = @"notReady";
    else if (castStatus == readyToConnect)
        castStatusString = @"readyToConnect";
    else if (castStatus == connecting)
        castStatusString = @"connecting";
    else if (castStatus == connected)
        castStatusString = @"connected";
    
    [[NSNotificationCenter defaultCenter] postNotificationName:@"CastStatusDidChange" object:self userInfo:@{@"status": castStatusString}];
    
}

-(void) updateStatsFromDevice
{
    if(self.deviceManager != nil && self.deviceManager.connectionState == GCKConnectionStateConnected && self.mediaControlChannel.mediaStatus != nil)
    {
        self.mediaInformation = self.mediaControlChannel.mediaStatus.mediaInformation;
    }
}

// MARK: - ChannelDelegate -

- (void)onConnect:(ChannelClient *)client error:(NSError *)error
{
    if (error != nil)
    {
        [self.search start];
        NSLog(@"onConnect failed - %@",[error localizedDescription]);
    }
    self.isConnecting = NO;
    [self updateCastStatus];
}

- (void)onDisconnect:(ChannelClient *)client error:(NSError *)error
{
    self.app = nil;
    self.isConnecting = NO;
    [self updateCastStatus];
}

// MARK: - ServiceDiscoveryDelegate Methods -

// These two delegate method will help us know when to change the cast button status

-(void)onServiceFound:(Service *)service
{
    [self.services addObject: service];
    [self updateCastStatus];
}
-(void)onServiceLost:(Service *)service
{
    for (Service *s in self.services) {
        if ([s.id isEqualToString:service.id]) {
            [self.services removeObject:s];
            break;
        }
    }
    [self updateCastStatus];
}

- (void)onStop {
    [self.services removeAllObjects];
}

- (void)deviceDisconnected {
   // self.textChannel = nil;
    self.deviceManager = nil;
    self.selectedDevice = nil;
    NSLog(@"Device disconnected");
}

// [START device-scanner-listener]
#pragma mark - GCKDeviceScannerListener
- (void)deviceDidComeOnline:(GCKDevice *)device {
    NSLog(@"device found!! %@", device.friendlyName);
    [self updateCastStatus];
}

- (void)deviceDidGoOffline:(GCKDevice *)device {
    [self updateCastStatus];
}
// [END device-scanner-listener]

#pragma mark - GCKDeviceManagerDelegate

// [START launch-application]
- (void)deviceManagerDidConnect:(GCKDeviceManager *)deviceManager {
    NSLog(@"connected to %@", _selectedDevice.friendlyName);
    
    self.isConnecting = false;
    
    [self updateCastStatus];
    
    // Launch application after getting connected.
    NSInteger requestId = [self.deviceManager launchApplication:kGCKMediaDefaultReceiverApplicationID];
    
    if(requestId == kGCKInvalidRequestID)
    {
        NSLog(@"invalid");
    }
    else{
        NSLog(@"valid");
    }
}
// [END launch-application]

- (void)deviceManager:(GCKDeviceManager *)deviceManager
didConnectToCastApplication:(GCKApplicationMetadata *)applicationMetadata
            sessionID:(NSString *)sessionID
  launchedApplication:(BOOL)launchedApplication {
    if (launchedApplication) {
        NSLog(@"application has launched");
        
        self.mediaControlChannel = [[GCKMediaControlChannel alloc] init];
        self.mediaControlChannel.delegate = self;
        [self.deviceManager addChannel:self.mediaControlChannel];
        
        [self.mediaControlChannel requestStatus];
    }
    else{
        NSLog(@"application has not launched");
    }
    
}

- (void)deviceManager:(GCKDeviceManager *)deviceManager
didFailToConnectToApplicationWithError:(NSError *)error {
   // [self showError:error];
    
    [self deviceDisconnected];
    [self updateCastStatus];
}

- (void)deviceManager:(GCKDeviceManager *)deviceManager
didFailToConnectWithError:(GCKError *)error {
  //  [self showError:error];
    
    [self deviceDisconnected];
    [self updateCastStatus];
}

- (void)deviceManager:(GCKDeviceManager *)deviceManager
didDisconnectWithError:(GCKError *)error {
    NSLog(@"Received notification that device disconnected");
    
   /* if (error != nil) {
        [self showError:error];
    }
   */
    [self deviceDisconnected];
    [self updateCastStatus];
    
}

- (void)deviceManager:(GCKDeviceManager *)deviceManager
didReceiveStatusForApplication:(GCKApplicationMetadata *)applicationMetadata {
 //   self.applicationMetadata = applicationMetadata;
    
    NSLog(@"Received device status: %@", applicationMetadata);
}

-(void)connectToChromeDevice:(GCKDevice *)device
{
    // [START device-selection]
    
    NSString* identifier = [[NSBundle mainBundle] bundleIdentifier];
    self.deviceManager = [[GCKDeviceManager alloc] initWithDevice:device clientPackageName:identifier];
    self.deviceManager.delegate = self;
    [self.deviceManager connect];
    self.isConnecting = YES;
    [self updateCastStatus];
    
    [self updateStatsFromDevice];
}

-(void)launchApp:(NSString*)videoId :(NSString*)videoName :(NSString*) videoThumbnail
{
    NSDictionary* dict = @{
                           @"videoId":videoId,
                           @"videoName":videoName,
                           @"videoThumnail":videoThumbnail
                           };
    NSError* error;
    NSData* jsonData = [NSJSONSerialization dataWithJSONObject:dict options:NSJSONWritingPrettyPrinted error:&error];
    
    if(!jsonData)
    {
        NSLog(@"error is %@", error);
    }
    else
    {
        NSString* jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        
        [self.app publishWithEvent:@"play" message:jsonString];
    }
    
}

-(void)launchAppOnChromeDevice
{
    
    if(self.deviceManager == nil || self.deviceManager.connectionState != GCKConnectionStateConnected)
    {
        return ;
    }
    
    GCKMediaMetadata* metadata = [[GCKMediaMetadata alloc] init];
    [metadata setString:@"Big Buck Bunny (2008)" forKey:kGCKMetadataKeyTitle];
    [metadata setString:@"Big Buck Bunny" forKey:kGCKMetadataKeySubtitle];
    
    NSURL *url = [NSURL URLWithString:@"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg"];
    
    GCKImage* img = [[GCKImage alloc] initWithURL:url width:480 height:360];
    
    [metadata addImage:img];
    
     GCKMediaInformation* mediaInformation = [[GCKMediaInformation alloc] initWithContentID:@"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" streamType:GCKMediaStreamTypeNone contentType:@"video/mp4" metadata:metadata streamDuration:0 mediaTracks:nil textTrackStyle: nil customData:nil];
     
    NSInteger requestId = [[self mediaControlChannel] loadMedia:mediaInformation autoplay:true];
    
    if(requestId == kGCKInvalidRequestID)
    {
        NSLog(@"invalid");
    }
    else{
        NSLog(@"valid");
    }
    
}

@end
