package org.agilewiki.jactor2.modules.impl;

import org.agilewiki.jactor2.core.closeable.Closeable;
import org.agilewiki.jactor2.core.impl.mtReactors.NonBlockingReactorMtImpl;
import org.agilewiki.jactor2.core.impl.mtReactors.ReactorMtImpl;
import org.agilewiki.jactor2.core.plant.PlantImpl;
import org.agilewiki.jactor2.core.reactors.NonBlockingReactor;
import org.agilewiki.jactor2.core.requests.AsyncRequest;
import org.agilewiki.jactor2.core.requests.AsyncResponseProcessor;
import org.agilewiki.jactor2.core.requests.ExceptionHandler;
import org.agilewiki.jactor2.modules.Activator;
import org.agilewiki.jactor2.modules.DependencyNotPresentException;
import org.agilewiki.jactor2.modules.Facility;
import org.agilewiki.jactor2.modules.MPlant;
import org.agilewiki.jactor2.modules.properties.immutable.ImmutableProperties;
import org.agilewiki.jactor2.modules.properties.transactions.ImmutablePropertyChanges;
import org.agilewiki.jactor2.modules.properties.transactions.PropertyChange;
import org.agilewiki.jactor2.modules.pubSub.SubscribeAReq;
import org.agilewiki.jactor2.modules.pubSub.Subscription;
import org.agilewiki.jactor2.modules.transactions.properties.*;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.SortedMap;

public class FacilityImpl extends NonBlockingReactorMtImpl {
    protected PropertiesProcessor propertiesProcessor;

    private String name;

    private MPlantImpl plantImpl;

    private FacilityImpl plantFacilityImpl;

    public FacilityImpl(final int _initialOutboxSize, final int _initialLocalQueueSize) {
        super(PlantImpl.getSingleton().getInternalReactor() == null ? null : PlantImpl.getSingleton().getInternalReactor(),
                _initialOutboxSize, _initialLocalQueueSize);
        plantImpl = MPlantImpl.getSingleton();
    }

    public void setName(final String _name) throws Exception {
        validateName(_name);
        name = _name;
        plantFacilityImpl = plantImpl.getInternalFacility().asFacilityImpl();
        propertiesProcessor = new PropertiesProcessor(this.getFacility());
        tracePropertyChangesAReq().signal();
        String dependencyPrefix = MPlantImpl.dependencyPrefix(name);
        PropertiesProcessor plantProperties = plantFacilityImpl.getPropertiesProcessor();
        ImmutableProperties dependencies =
                plantProperties.getImmutableState().subMap(dependencyPrefix);
        Iterator<String> dit = dependencies.keySet().iterator();
        while (dit.hasNext()) {
            String d = dit.next();
            String dependencyName = d.substring(dependencyPrefix.length());
            FacilityImpl dependency = plantImpl.getFacilityImpl(dependencyName);
            if (dependency == null)
                throw new DependencyNotPresentException(dependencyName);
            dependency.addCloseable(this);
        }
    }

    public AsyncRequest<Void> startFacilityAReq() {
        return new AsyncRequest<Void>(this.asReactor()) {
            AsyncRequest<Void> dis = this;

            AsyncResponseProcessor<Void> registerResponseProcessor =
                    new AsyncResponseProcessor<Void>() {
                        @Override
                        public void processAsyncResponse(Void _response) {
                            parentReactor.addCloseable(FacilityImpl.this);
                            String activatorClassName = MPlant.getActivatorClassName(name);
                            if (activatorClassName == null)
                                dis.processAsyncResponse(null);
                            else {
                                send(activateAReq(activatorClassName), new AsyncResponseProcessor<String>() {
                                    @Override
                                    public void processAsyncResponse(final String _failure) throws Exception {
                                        if (_failure == null) {
                                            System.out.println("registered " + name);
                                            dis.processAsyncResponse(null);
                                            return;
                                        }
                                        close(false, _failure);
                                    }
                                });
                            }
                        }
                    };

            @Override
            public void processAsyncRequest() throws Exception {
                send(registerFacilityAReq(), registerResponseProcessor);
            }
        };
    }

    public Facility asFacility() {
        return (Facility) asReactor();
    }

    public Facility getFacility() {
        return (Facility) getReactor();
    }

    public String getName() {
        return name;
    }

    public PropertiesProcessor getPropertiesProcessor() {
        return propertiesProcessor;
    }

    protected void validateName(final String _name) throws Exception {
        if (_name == null) {
            throw new IllegalArgumentException("name may not be null");
        }
        if (_name.length() == 0) {
            throw new IllegalArgumentException("name may not be empty");
        }
        if (_name.contains(" ")) {
            throw new IllegalArgumentException("name may not contain spaces: "
                    + _name);
        }
        if (_name.contains("~")) {
            throw new IllegalArgumentException("name may not contain ~: "
                    + _name);
        }
        if (_name.equals(MPlantImpl.PLANT_NAME)) {
            if (getParentReactor() != null)
                throw new IllegalArgumentException("name may not be " + MPlantImpl.PLANT_NAME);
        } else if (MPlant.getFacility(_name) != null) {
            throw new IllegalStateException("facility by that name already exists");
        }
    }

    @Override
    public void close() throws Exception {
        close(false, null);
    }

    public void stop() throws Exception {
        close(true, null);
    }

    public void fail(final String reason) throws Exception {
        close(false, reason);
    }

    private void close(final boolean _stop, final String _reasonForFailure) throws Exception {
        if (_reasonForFailure != null && _stop)
            throw new IllegalArgumentException("can not both stop and fail");
        if (startedClosing()) {
            plantImpl.getInternalFacility().putPropertyAReq(MPlantImpl.failedKey(name), null, _reasonForFailure).
                    signal();
            plantImpl.getInternalFacility().putPropertyAReq(MPlantImpl.stoppedKey(name), null, true).
                    signal();
            return;
        }
        final MPlantImpl plantImpl = MPlantImpl.getSingleton();
        if ((plantImpl != null) &&
                plantImpl.getInternalFacility().asFacilityImpl() != this &&
                !plantImpl.getInternalFacility().asFacilityImpl().startedClosing()) {
            plantImpl.updateFacilityStatusAReq(null, name, _stop, _reasonForFailure).signal();
        }
        super.fail(_reasonForFailure);
    }

    private AsyncRequest<Void> registerFacilityAReq() {
        final MPlantImpl plantImpl = MPlantImpl.getSingleton();
        return plantImpl.updateFacilityStatusAReq(FacilityImpl.this.asFacility(), name, false, null);
    }

    /**
     * Returns the value of a property.
     *
     * @param propertyName The property name.
     * @return The property value, or null.
     */
    public Object getProperty(final String propertyName) {
        return propertiesProcessor.getImmutableState().get(propertyName);
    }

    public AsyncRequest<Void> putPropertyAReq(final String _propertyName,
                                              final Boolean _propertyValue) {
        return propertiesProcessor.putAReq(_propertyName, _propertyValue);
    }

    public AsyncRequest<Void> putPropertyAReq(final String _propertyName,
                                              final String _propertyValue) {
        return propertiesProcessor.putAReq(_propertyName, _propertyValue);
    }

    public AsyncRequest<Void> putPropertyAReq(final String _propertyName,
                                              final Boolean _expectedValue,
                                              final Boolean _propertyValue) {
        return propertiesProcessor.compareAndSetAReq(_propertyName, _expectedValue, _propertyValue);
    }

    public AsyncRequest<Void> putPropertyAReq(final String _propertyName,
                                              final String _expectedValue,
                                              final String _propertyValue) {
        return propertiesProcessor.compareAndSetAReq(_propertyName, _expectedValue, _propertyValue);
    }

    protected ClassLoader getClassLoader() throws Exception {
        return getClass().getClassLoader();
    }

    public AsyncRequest<ClassLoader> getClassLoaderAReq() {
        return new AsyncBladeRequest<ClassLoader>() {
            @Override
            public void processAsyncRequest() throws Exception {
                processAsyncResponse(getClassLoader());
            }
        };
    }

    public AsyncRequest<String> activateAReq(final String _activatorClassName) {
        return new AsyncBladeRequest<String>() {
            @Override
            public void processAsyncRequest() throws Exception {
                setExceptionHandler(new ExceptionHandler<String>() {
                    @Override
                    public String processException(Exception e) throws Exception {
                        getLogger().error("activation exception, facility " + name, e);
                        return "activation exception, " + e;
                    }
                });
                final Class<?> initiatorClass = getClassLoader().loadClass(
                        _activatorClassName);
                final Constructor<?> constructor = initiatorClass.getConstructor(NonBlockingReactor.class);
                final Activator activator = (Activator) constructor.newInstance(asReactor());
                send(activator.startAReq(), this, null);
            }
        };
    }

    public AsyncRequest<Subscription<ImmutablePropertyChanges>> tracePropertyChangesAReq() {
        return new SubscribeAReq<ImmutablePropertyChanges>(propertiesProcessor.changeBus, asReactor()) {
            @Override
            protected void processContent(final ImmutablePropertyChanges _content)
                    throws Exception {
                SortedMap<String, PropertyChange> readOnlyChanges = _content.readOnlyChanges;
                final Iterator<PropertyChange> it = readOnlyChanges.values().iterator();
                while (it.hasNext()) {
                    final PropertyChange propertyChange = it.next();
                    String[] args = {
                            name,
                            propertyChange.name,
                            "" + propertyChange.oldValue,
                            "" + propertyChange.newValue
                    };
                    logger.info("\n    facility={}\n    key={}\n    old={}\n    new={}", args);
                }
            }
        };
    }

    public void facilityPoll() throws Exception {
        Iterator<Closeable> it = getCloseableSet().iterator();
        while (it.hasNext()) {
            Closeable closeable = it.next();
            if (!(closeable instanceof ReactorMtImpl))
                continue;
            ReactorMtImpl reactor = (ReactorMtImpl) closeable;
            reactor.reactorPoll();
        }
        reactorPoll();
    }
}
