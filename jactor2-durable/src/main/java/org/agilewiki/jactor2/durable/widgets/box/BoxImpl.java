package org.agilewiki.jactor2.durable.widgets.box;

import org.agilewiki.jactor2.common.CFacility;
import org.agilewiki.jactor2.common.widgets.InternalWidget;
import org.agilewiki.jactor2.common.widgets.buffers.UnmodifiableByteBufferFactory;
import org.agilewiki.jactor2.core.blades.transmutable.Transmutable;
import org.agilewiki.jactor2.durable.widgets.DurableFactory;
import org.agilewiki.jactor2.durable.widgets.DurableImpl;
import org.agilewiki.jactor2.durable.widgets.UnexpectedValueException;
import org.agilewiki.jactor2.durable.widgets.string.StringFactory;
import org.agilewiki.jactor2.durable.widgets.string.StringImpl;

import java.nio.ByteBuffer;

public class BoxImpl extends DurableImpl {

    protected InternalWidget content;

    protected StringImpl factoryKey;

    protected int byteLen;

    public BoxImpl(DurableFactory _widgetFactory, InternalWidget _parent, ByteBuffer _byteBuffer) {
        super(_widgetFactory, _parent, _byteBuffer);
        CFacility facility = _widgetFactory.getFacility();
        factoryKey = (StringImpl) facility.newInternalWidget(StringFactory.FACTORY_NAME, this,
                _byteBuffer);
        content = facility.newInternalWidget(factoryKey.asWidget().getValue(), this,
                _byteBuffer);
        byteLen = factoryKey.getBufferSize() + content.getBufferSize();
    }

    public BoxImpl(CFacility _facility, InternalWidget _parent) {
        super(BoxFactory.getFactory(_facility), _parent, null);
        content = new DurableImpl(_facility, this);
        factoryKey = new StringImpl(_facility, this,
                content.getInternalWidgetFactory().getFactoryKey());
        byteLen = factoryKey.getBufferSize() + content.getBufferSize();
    }

    @Override
    public BoxFactory getInternalWidgetFactory() {
        return (BoxFactory) super.getInternalWidgetFactory();
    }

    @Override
    public _Box asWidget() {
        return (_Box) super.asWidget();
    }

    @Override
    protected _Box newWidget() {
        return new _Box();
    }

    @Override
    public String apply(final String _path,
                        final String _params,
                        final UnmodifiableByteBufferFactory _contentFactory)
            throws Exception {
        return null;
    }

    @Override
    public Transmutable<UnmodifiableByteBufferFactory> recreate(
            final UnmodifiableByteBufferFactory _unmodifiable) {
        return new BoxImpl(getInternalWidgetFactory(),
                getParent(), _unmodifiable.duplicateByteBuffer());
    }

    @Override
    public int getBufferSize() {
        return byteLen;
    }

    @Override
    protected void _serialize(final ByteBuffer _byteBuffer) {
        factoryKey.serialize(_byteBuffer);
        content.serialize(_byteBuffer);
    }

    @Override
    public void childChange(final int _delta) {
        byteLen += _delta;
        notifyParent(_delta);
    }

    public class _Box extends _Durable implements DurableBox {

        @Override
        public String boxedFactoryKey() {
            return null;
        }

        @Override
        public void expectedFactoryKey(String _expected) throws UnexpectedValueException {

        }

        @Override
        public InternalWidget getBoxedInternalWidget() {
            return null;
        }

        @Override
        public void putCopy(InternalWidget _internalWidget) {

        }
    }
}
