/**
 * Copyright 2009-2011, Qualcomm Innovation Center, Inc.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.alljoyn.bus;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;
import org.alljoyn.bus.ifaces.DBusProxyObj;
import org.alljoyn.bus.ifaces.AllJoynProxyObj;
import org.alljoyn.bus.ifaces.Introspectable;

import static junit.framework.Assert.*;
import junit.framework.TestCase;

public class BusAttachmentTest extends TestCase {
    static {
        System.loadLibrary("alljoyn_java");
    }

    public class Emitter implements EmitterInterface, BusObject {
        private SignalEmitter emitter;

        public Emitter() { 
            emitter = new SignalEmitter(this); 
            emitter.setTimeToLive(1000);
        }
        public void Emit(String string) throws BusException {
            emitter.getInterface(EmitterInterface.class).Emit(string);
        }
    }

    public class Service implements BusObject {
    }

    public class SimpleService implements SimpleInterface, BusObject {
        public String Ping(String str) { 
            MessageContext ctx = bus.getMessageContext();
            assertEquals(false, ctx.isUnreliable);
            assertEquals("/simple", ctx.objectPath);
            assertEquals("org.alljoyn.bus.SimpleInterface", ctx.interfaceName);
            assertEquals("Ping", ctx.memberName);
            assertEquals("s", ctx.signature);
            assertEquals("", ctx.authMechanism);
            return str; 
        }
    }

    public class SecureService implements SecureInterface, BusObject {
        public String Ping(String str) { return str; }
    }

    public class ExceptionService implements SimpleInterface, BusObject {
        public String Ping(String str) throws BusException { throw new BusException("ExceptionService"); }
    }

    public class AnnotationService implements AnnotationInterface,
                                              BusObject {
        public String DeprecatedMethod(String str) throws BusException { return str; }
        public void NoReplyMethod(String str) throws BusException {}
        public void DeprecatedNoReplyMethod(String str) throws BusException {}
        public void DeprecatedSignal(String str) throws BusException {}
    }

    private BusAttachment bus;
    private int handledSignals1;
    private int handledSignals2;
    private int handledSignals3;
    private int handledSignals4;
    private boolean pinRequested;
    private BusAttachment otherBus;
    private AllJoynDaemon daemon;
    private String name;
    private String address;
    private boolean isAndroid = false; // running on android device?

    public BusAttachmentTest(String name) {
        super(name);
        if ("The Android Project".equals(System.getProperty("java.vendor"))) 
        {
            isAndroid = true;
        }
    }

    public void setUp() throws Exception {
        bus = null;
        name = "org.alljoyn.bus.BusAttachmentTest.advertise";
        address = System.getProperty("org.alljoyn.bus.address", "unix:abstract=bluebus");
    }

    public void tearDown() throws Exception {
        System.setProperty("org.alljoyn.bus.address", address);

        if (bus != null) {
            bus.getDBusProxyObj().ReleaseName("org.alljoyn.bus.BusAttachmentTest");
            bus.disconnect();
            bus = null;
        }

        if (otherBus != null) {
            otherBus.getAllJoynProxyObj().CancelAdvertiseName(name);
            otherBus.getDBusProxyObj().ReleaseName(name);
            otherBus.disconnect();
            otherBus = null;
        }

        if (daemon != null) {
            daemon.stop();
        }
    }

    public void testEmitSignalFromUnregisteredSource() throws Exception {
        boolean thrown = false;
        try {
            Emitter emitter = new Emitter();
            emitter.Emit("emit1");
        } catch (BusException ex) {
            thrown = true;
        } finally {
            assertTrue(thrown);
        }
    }

    public void signalHandler1(String string) throws BusException {
        ++handledSignals1;
    }

    public void signalHandler2(String string) throws BusException {
        ++handledSignals2;
    }

    public void testRegisterMultipleSignalHandlersForOneSignal() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        Emitter emitter = new Emitter();
        status = bus.registerBusObject(emitter, "/emitter");
        assertEquals(Status.OK, status);

        handledSignals1 = handledSignals2 = 0;
        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit",
                                            this, getClass().getMethod("signalHandler1", String.class));
        assertEquals(Status.OK, status);
        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit",
                                            this, getClass().getMethod("signalHandler2", String.class));
        assertEquals(Status.OK, status);
        emitter.Emit("emit1");
        Thread.currentThread().sleep(500);
        assertEquals(1, handledSignals1);
        assertEquals(1, handledSignals2);

        handledSignals1 = handledSignals2 = 0;
        bus.deregisterSignalHandler(this, getClass().getMethod("signalHandler1", String.class));
        emitter.Emit("emit2");
        Thread.currentThread().sleep(500);
        assertEquals(0, handledSignals1);
        assertEquals(1, handledSignals2);
    }

    public void testRegisterSignalHandlerNoLocalObject() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit", 
                                            this, getClass().getMethod("signalHandler1", String.class));
        assertEquals(Status.OK, status);
    }

    public class SignalHandlers {
        //@BusSignalHandler(iface = "org.alljoyn.bus.EmitterFoo", signal = "Emit") // ClassNotFound exception
        public void signalHandler1(String string) throws BusException {
        }

        @BusSignalHandler(iface = "org.alljoyn.bus.EmitterBarInterface", signal = "Emit")
        public void signalHandler2(String string) throws BusException {
        }

        @BusSignalHandler(iface = "org.alljoyn.bus.EmitterBarInterface", signal = "EmitBar")
        public void signalHandler3(String string) throws BusException {
        }
    }

    public void testRegisterSignalHandlersNoLocalObject() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        SignalHandlers handlers = new SignalHandlers();
        status = bus.registerSignalHandlers(handlers);
        assertEquals(Status.OK, status);
    }

    public void testRegisterSignalHandlerBeforeLocalObject() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);
        
        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit",
                                            this, getClass().getMethod("signalHandler1", String.class));
        assertEquals(Status.OK, status);

        Emitter emitter = new Emitter();
        status = bus.registerBusObject(emitter, "/emitter");
        assertEquals(Status.OK, status);

        handledSignals1 = 0;
        emitter.Emit("emit1");
        Thread.currentThread().sleep(500);
        assertEquals(1, handledSignals1);
    }

    public void testRegisterSourcedSignalHandler() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        Emitter emitter = new Emitter();
        status = bus.registerBusObject(emitter, "/emitter");
        assertEquals(Status.OK, status);

        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit",
                                           this, getClass().getMethod("signalHandler1", String.class),
                                           "/emitter");
        assertEquals(Status.OK, status);
        handledSignals1 = 0;
        emitter.Emit("emit1");
        Thread.currentThread().sleep(500);
        assertEquals(1, handledSignals1);

        bus.deregisterSignalHandler(this, getClass().getMethod("signalHandler1", String.class));
        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit",
                                           this, getClass().getMethod("signalHandler1", String.class),
                                           "/doesntexist");
        assertEquals(Status.OK, status);
        handledSignals1 = 0;
        emitter.Emit("emit1");
        Thread.currentThread().sleep(500);
        assertEquals(0, handledSignals1);
    }

    private void signalHandler3(String string) throws BusException {
        ++handledSignals3;
    }

    public void testRegisterPrivateSignalHandler() throws Exception {
        boolean thrown = false;
        try {
            bus = new BusAttachment(getClass().getName());
            Status status = bus.connect();
            assertEquals(Status.OK, status);
    
            status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit", 
                                                this, getClass().getMethod("signalHandler3", String.class));
            assertEquals(Status.OK, status);
        } catch (NoSuchMethodException ex) {
            thrown = true;
        } finally {
            assertTrue(thrown);
        }
    }

    public void testReregisterNameAfterDisconnect() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        DBusProxyObj control = bus.getDBusProxyObj();
        DBusProxyObj.RequestNameResult res = control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                                                DBusProxyObj.REQUEST_NAME_NO_FLAGS);
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner, res);

        bus.disconnect();

        status = bus.connect();
        assertEquals(Status.OK, status);

        res = control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                  DBusProxyObj.REQUEST_NAME_NO_FLAGS);
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner, res);
    }

    public void testRegisterSameLocalObjectTwice() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        Service service = new Service();
        status = bus.registerBusObject(service, "/testobject");
        assertEquals(Status.OK, status);
        status = bus.registerBusObject(service, "/testobject");
        assertEquals(Status.BUS_OBJ_ALREADY_EXISTS, status);
    }

    public void signalHandler4(String string) throws BusException {
        ++handledSignals4;
        MessageContext ctx = bus.getMessageContext();
        assertEquals(true, ctx.isUnreliable);
        assertEquals("/emitter", ctx.objectPath);
        assertEquals("org.alljoyn.bus.EmitterInterface", ctx.interfaceName);
        assertEquals("Emit", ctx.memberName);
        assertEquals("s", ctx.signature);
        assertEquals("", ctx.authMechanism);
    }

    public void testSignalMessageContext() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);

        Emitter emitter = new Emitter();
        status = bus.registerBusObject(emitter, "/emitter");
        assertEquals(Status.OK, status);

        handledSignals4 = 0;
        status = bus.registerSignalHandler("org.alljoyn.bus.EmitterInterface", "Emit",
                                            this, getClass().getMethod("signalHandler4", String.class));
        assertEquals(Status.OK, status);

        emitter.Emit("emit4");
        Thread.currentThread().sleep(500);
        assertEquals(1, handledSignals4);
    }

    public void testMethodMessageContext() throws Exception {
        bus = new BusAttachment(getClass().getName());
        SimpleService service = new SimpleService();
        assertEquals(Status.OK, bus.registerBusObject(service, "/simple"));

        assertEquals(Status.OK, bus.connect());
        DBusProxyObj control = bus.getDBusProxyObj();
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner,
                     control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                         DBusProxyObj.REQUEST_NAME_NO_FLAGS));
        ProxyBusObject proxyObj = bus.getProxyBusObject("org.alljoyn.bus.BusAttachmentTest",
                                                        "/simple", 
                                                        AllJoynProxyObj.SESSION_ID_ANY,
                                                        new Class[] { SimpleInterface.class });
        SimpleInterface proxy = proxyObj.getInterface(SimpleInterface.class);
        proxy.Ping("hello");
    }

    /* ALLJOYN-26 */
    public void testRegisterUnknownAuthListener() throws Exception {
        bus = new BusAttachment(getClass().getName());
        bus.registerKeyStoreListener(new NullKeyStoreListener());
        Status status = bus.connect();
        assertEquals(Status.OK, status);
        status = bus.registerAuthListener("ALLJOYN_BOGUS_KEYX", new AuthListener() {
                public boolean requested(String mechanism, int count, String userName, 
                                         AuthRequest[] requests) {
                    return false;
                }
                public void completed(String mechanism, boolean authenticated) {}
            });
        assertEquals(Status.BUS_INVALID_AUTH_MECHANISM, status);
    }

    private AuthListener authListener = new AuthListener() {
            public boolean requested(String mechanism, int count, String userName, 
                                     AuthRequest[] requests) {
                pinRequested = true;
                for (AuthRequest request : requests) {
                    if (request instanceof PasswordRequest) {
                        ((PasswordRequest) request).setPassword("123456".toCharArray());
                    } else {
                        return false;
                    }
                }
                return true;
            }

            public void completed(String mechanism, boolean authenticated) {}
        };

    public void testRegisterAuthListenerBeforeConnect() throws Exception {
        bus = new BusAttachment(getClass().getName());
        bus.registerKeyStoreListener(new NullKeyStoreListener());
        SecureService service = new SecureService();
        Status status = bus.registerBusObject(service, "/secure");
        assertEquals(Status.OK, status);
        status = bus.registerAuthListener("ALLJOYN_SRP_KEYX", authListener);
        assertEquals(Status.OK, status);
        status = bus.connect();
        assertEquals(Status.OK, status);
        DBusProxyObj control = bus.getDBusProxyObj();
        DBusProxyObj.RequestNameResult res = control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                                                DBusProxyObj.REQUEST_NAME_NO_FLAGS);
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner, res);

        BusAttachment otherBus = new BusAttachment(getClass().getName());
        otherBus.registerKeyStoreListener(new NullKeyStoreListener());
        status = otherBus.registerAuthListener("ALLJOYN_SRP_KEYX", authListener);
        assertEquals(Status.OK, status);
        status = otherBus.connect();
        assertEquals(Status.OK, status);
        ProxyBusObject proxyObj = otherBus.getProxyBusObject("org.alljoyn.bus.BusAttachmentTest",
                                                             "/secure", 
                                                             AllJoynProxyObj.SESSION_ID_ANY,
                                                             new Class[] { SecureInterface.class });
        SecureInterface proxy = proxyObj.getInterface(SecureInterface.class);
        pinRequested = false;
        proxy.Ping("hello");
        assertEquals(true, pinRequested);

        otherBus.disconnect();
        otherBus = null;
    }

    public void testGetUniqueName() throws Exception {
        bus = new BusAttachment(getClass().getName());
        Status status = bus.connect();
        assertEquals(Status.OK, status);
        assertFalse(bus.getUniqueName().equals(""));
    }

    public void testMethodSignalAnnotation() throws Exception {
        bus = new BusAttachment(getClass().getName());
        assertEquals(Status.OK, bus.connect());
        DBusProxyObj control = bus.getDBusProxyObj();
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner, 
                     control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                         DBusProxyObj.REQUEST_NAME_NO_FLAGS));

        AnnotationService service = new AnnotationService();
        assertEquals(Status.OK, bus.registerBusObject(service, "/annotation"));
        
        ProxyBusObject proxyObj = bus.getProxyBusObject("org.alljoyn.bus.BusAttachmentTest",
                                                        "/annotation",
                                                        AllJoynProxyObj.SESSION_ID_ANY,
                                                        new Class[] { Introspectable.class });

        Introspectable introspectable = proxyObj.getInterface(Introspectable.class);
        String actual = introspectable.Introspect();
        String expected = 
            "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n" +
            "\"http://standards.freedesktop.org/dbus/introspect-1.0.dtd\">\n" +
            "<node>\n" +
            "  <interface name=\"org.alljoyn.bus.AnnotationInterface\">\n" +
            "    <method name=\"DeprecatedMethod\">\n" +
            "      <arg type=\"s\" direction=\"in\"/>\n" + 
            "      <arg type=\"s\" direction=\"out\"/>\n" + 
            "      <annotation name=\"org.freedesktop.DBus.Deprecated\" value=\"true\"/>\n" +
            "    </method>\n" +
            "    <method name=\"DeprecatedNoReplyMethod\">\n" +
            "      <arg type=\"s\" direction=\"in\"/>\n" + 
            "      <annotation name=\"org.freedesktop.DBus.Method.NoReply\" value=\"true\"/>\n" +
            "      <annotation name=\"org.freedesktop.DBus.Deprecated\" value=\"true\"/>\n" +
            "    </method>\n" +
            "    <signal name=\"DeprecatedSignal\">\n" +
            "      <arg type=\"s\" direction=\"in\"/>\n" + 
            "      <annotation name=\"org.freedesktop.DBus.Deprecated\" value=\"true\"/>\n" +
            "    </signal>\n" +
            "    <method name=\"NoReplyMethod\">\n" +
            "      <arg type=\"s\" direction=\"in\"/>\n" + 
            "      <annotation name=\"org.freedesktop.DBus.Method.NoReply\" value=\"true\"/>\n" +
            "    </method>\n" +
            "  </interface>\n" +
            "  <interface name=\"org.freedesktop.DBus.Introspectable\">\n" +
            "    <method name=\"Introspect\">\n" +
            "      <arg name=\"data\" type=\"s\" direction=\"out\"/>\n" +
            "    </method>\n" +
            "  </interface>\n" +
            "</node>\n";
        assertEquals(expected, actual);
    }

    public void testNoReplyMethod() throws Exception {
        bus = new BusAttachment(getClass().getName());
        assertEquals(Status.OK, bus.connect());
        DBusProxyObj control = bus.getDBusProxyObj();
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner, 
                     control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                         DBusProxyObj.REQUEST_NAME_NO_FLAGS));

        AnnotationService service = new AnnotationService();
        assertEquals(Status.OK, bus.registerBusObject(service, "/annotation"));
        
        ProxyBusObject proxyObj = bus.getProxyBusObject("org.alljoyn.bus.BusAttachmentTest",
                                                        "/annotation",
                                                        AllJoynProxyObj.SESSION_ID_ANY,
                                                        new Class[] { AnnotationInterface.class });
        AnnotationInterface proxy = proxyObj.getInterface(AnnotationInterface.class);
        proxy.NoReplyMethod("noreply");
        /* No assert here, the test is to make sure the above call doesn't throw an exception. */
    }

    public void testMethodException() throws Exception {
        bus = new BusAttachment(getClass().getName());
        ExceptionService service = new ExceptionService();
        assertEquals(Status.OK, bus.registerBusObject(service, "/exception"));

        assertEquals(Status.OK, bus.connect());
        DBusProxyObj control = bus.getDBusProxyObj();
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner,
                     control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                         DBusProxyObj.REQUEST_NAME_NO_FLAGS));
        ProxyBusObject proxyObj = bus.getProxyBusObject("org.alljoyn.bus.BusAttachmentTest",
                                                        "/exception", 
                                                        AllJoynProxyObj.SESSION_ID_ANY,
                                                        new Class[] { SimpleInterface.class });
        SimpleInterface proxy = proxyObj.getInterface(SimpleInterface.class);
        try {
            proxy.Ping("hello");
        } catch (BusException ex) {
        }
    }

    private boolean found;
    private boolean lost;

    public void testFindNewName() throws Exception {
        if (!isAndroid) // Android device always fails this test
        {
            bus = new BusAttachment(getClass().getName());
            assertEquals(Status.OK, bus.connect());
            found = false;
            lost = false;
            assertEquals(Status.OK, bus.findName(name, new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    found = true;
                }

                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                    lost = true;
                }
            }));

            daemon = new AllJoynDaemon();
            System.setProperty("org.alljoyn.bus.address", daemon.address());
            otherBus = new BusAttachment(getClass().getName(), BusAttachment.RemoteMessage.Receive);
            assertEquals(Status.OK, otherBus.connect(name));

            Thread.currentThread().sleep(5 * 1000);
            assertEquals(Status.OK, bus.cancelFindName(name));
            assertEquals(true, found);
        }
    }

    public void testLostExistingName() throws Exception {
        if (!isAndroid) // Android device always fails this test
        {
        // The client part
        bus = new BusAttachment(getClass().getName());
        assertEquals(Status.OK, bus.connect());

        // A new daemon comes up and we have otherBus on which the service is running
        daemon = new AllJoynDaemon();
        System.setProperty("org.alljoyn.bus.address", daemon.address());
        otherBus = new BusAttachment(getClass().getName(), BusAttachment.RemoteMessage.Receive);
        assertEquals(Status.OK, otherBus.connect(name));
        
        found = false;
        lost = false;
        assertEquals(Status.OK, bus.findName(name, new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    System.out.println("Name is :  "+name);
                    found = true;
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                    lost = true;
                }
            }));
        
        // Advertise a name and then cancel it.
        Thread.currentThread().sleep(2 * 1000);
        assertEquals(AllJoynProxyObj.AdvertiseNameResult.Success, otherBus.getAllJoynProxyObj().AdvertiseName("org.alljoyn.bus.BusAttachmentTest.advertise.happy"));
        Thread.currentThread().sleep(2 * 1000);
        assertEquals(true , found);
        assertEquals(AllJoynProxyObj.CancelAdvertiseNameResult.Success,otherBus.getAllJoynProxyObj().CancelAdvertiseName("org.alljoyn.bus.BusAttachmentTest.advertise.happy"));
        Thread.currentThread().sleep(10 * 1000);
        assertEquals(true, lost);
        }
    }

    public void testFindExistingName() throws Exception {
        if (!isAndroid) // Android device always fails this test
        {
            bus = new BusAttachment(getClass().getName());
            assertEquals(Status.OK, bus.connect());

            daemon = new AllJoynDaemon();
            System.setProperty("org.alljoyn.bus.address", daemon.address());
            otherBus = new BusAttachment(getClass().getName(), BusAttachment.RemoteMessage.Receive);
            assertEquals(Status.OK, otherBus.connect(name));

            found = false;
            lost = false;
            assertEquals(Status.OK, bus.findName(name, new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    found = true;
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                    lost = true;
                }
            }));
            Thread.currentThread().sleep(2 * 1000);
            assertEquals(Status.OK, bus.cancelFindName(name));
            assertEquals(true, found);
        }
    }

    public void testFindNullName() throws Exception {
        bus = new BusAttachment(getClass().getName());
        assertEquals(Status.OK, bus.connect());
        boolean thrown = false;
        try {
            bus.findName(null, new FindNameListener() {
                    public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    }
                    public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                    }
                });
        } catch (IllegalArgumentException ex) {
            thrown = true;
        }
        assertTrue(thrown);
        thrown = false;
        try {
            bus.cancelFindName(null);
        } catch (IllegalArgumentException ex) {
            thrown = true;
        }
        assertTrue(thrown);
    }        

    private boolean foundNameA;
    private boolean foundNameB;

    public void testFindMultipleNames() throws Exception {
        if (!isAndroid) // Android device always fails this test
        {
            bus = new BusAttachment(getClass().getName());
            assertEquals(Status.OK, bus.connect());
            foundNameA = foundNameB = false;
            assertEquals(Status.OK, bus.findName("name.A", new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    assertEquals("name.A", name);
                    foundNameA = true;
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                }
            }));
            assertEquals(Status.OK, bus.findName("name.B", new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    assertEquals("name.B", name);
                    foundNameB = true;
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                }
            }));
            daemon = new AllJoynDaemon();
            System.setProperty("org.alljoyn.bus.address", daemon.address());
            otherBus = new BusAttachment(getClass().getName(), BusAttachment.RemoteMessage.Receive);
            assertEquals(Status.OK, otherBus.connect());
            assertEquals(AllJoynProxyObj.AdvertiseNameResult.Success, otherBus.getAllJoynProxyObj().AdvertiseName("name.A"));
            assertEquals(AllJoynProxyObj.AdvertiseNameResult.Success, otherBus.getAllJoynProxyObj().AdvertiseName("name.B"));
            Thread.currentThread().sleep(4 * 1000);

            assertEquals(true, foundNameA);
            assertEquals(true, foundNameB);
        
            assertEquals(AllJoynProxyObj.CancelAdvertiseNameResult.Success, 
                     otherBus.getAllJoynProxyObj().CancelAdvertiseName("name.A"));
            assertEquals(AllJoynProxyObj.CancelAdvertiseNameResult.Success, 
                     otherBus.getAllJoynProxyObj().CancelAdvertiseName("name.B"));

            assertEquals(Status.OK, bus.cancelFindName("name.B"));
            foundNameA = foundNameB = false;
            assertEquals(AllJoynProxyObj.AdvertiseNameResult.Success, otherBus.getAllJoynProxyObj().AdvertiseName("name.A"));
            assertEquals(AllJoynProxyObj.AdvertiseNameResult.Success, otherBus.getAllJoynProxyObj().AdvertiseName("name.B"));
            Thread.currentThread().sleep(2 * 1000);

            assertEquals(Status.OK, bus.cancelFindName("name.A"));
            assertEquals(AllJoynProxyObj.CancelAdvertiseNameResult.Success, 
                     otherBus.getAllJoynProxyObj().CancelAdvertiseName("name.A"));
            assertEquals(AllJoynProxyObj.CancelAdvertiseNameResult.Success, 
                     otherBus.getAllJoynProxyObj().CancelAdvertiseName("name.B"));
            assertEquals(true, foundNameA);
            assertEquals(false, foundNameB);
        }
    }

    public void testCancelFindNameInsideListener() throws Exception {
        if (!isAndroid) // Android device always fails this test
        {
            bus = new BusAttachment(getClass().getName());
            assertEquals(Status.OK, bus.connect());
            found = false;
            assertEquals(Status.OK, bus.findName(name, new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                    if (!found) {
                        found = true;
                        assertEquals(Status.OK, bus.cancelFindName(namePrefix));
                    }
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                }
            }));

            daemon = new AllJoynDaemon();
            System.setProperty("org.alljoyn.bus.address", daemon.address());
            otherBus = new BusAttachment(getClass().getName(), BusAttachment.RemoteMessage.Receive);
            assertEquals(Status.OK, otherBus.connect(name));

            Thread.currentThread().sleep(1 * 1000);
            assertEquals(true, found);
        }
    }

    public void testFindSameName() throws Exception {
        bus = new BusAttachment(getClass().getName());
        assertEquals(Status.OK, bus.connect());
        found = false;
        assertEquals(Status.OK, bus.findName(name, new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                }
            }));
        assertEquals(Status.ALREADY_FINDING, bus.findName(name, new FindNameListener() {
                public void foundName(String name, String guid, String namePrefix, String busAddress) {
                }
                public void lostAdvertisedName(String name, String guid, String namePrefix, String busAddress) {
                }
            }));
        assertEquals(Status.OK, bus.cancelFindName(name));
    }
}
