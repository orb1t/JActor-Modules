package org.agilewiki.jactor2.durable.widgets.box;

import org.agilewiki.jactor2.common.CFacility;
import org.agilewiki.jactor2.common.widgets.*;
import org.agilewiki.jactor2.common.widgets.buffers.UnmodifiableByteBufferFactory;
import org.agilewiki.jactor2.durable.transactions.DurableTransaction;
import org.agilewiki.jactor2.durable.widgets.InvalidWidgetContentException;
import org.agilewiki.jactor2.durable.widgets.UnexpectedValueException;
import org.agilewiki.jactor2.durable.widgets.string.StringFactory;
import org.agilewiki.jactor2.durable.widgets.string.StringImpl;

import java.nio.ByteBuffer;

public class BoxImpl extends WidgetImpl {

    public static DurableTransaction expectedFactoryKeyTransaction(final CFacility facility,
                                                                   final String _path,
                                                                   final String _value) {
        return new DurableTransaction(_path, "expectedFactoryKey", new StringImpl(facility, null, _value).asWidget());
    }

    public static DurableTransaction putCopyTransaction(final CFacility facility,
                                                        final String _path,
                                                        final Widget _value) {
        return new DurableTransaction(_path, "putCopy", _value);
    }

    protected _Widget content;

    protected StringImpl factoryKey;

    protected int byteLen;

    public BoxImpl(WidgetFactory _widgetFactory, InternalWidget _parent, ByteBuffer _byteBuffer) {
        super(_widgetFactory, _parent, _byteBuffer);
        CFacility facility = _widgetFactory.getFacility();
        factoryKey = (StringImpl) facility.newInternalWidget(StringFactory.FACTORY_NAME, this,
                _byteBuffer);
        content = facility.newInternalWidget(factoryKey.asWidget().getValue(), this,
                _byteBuffer).asWidget();
        byteLen = factoryKey.getBufferSize() + content.getBufferSize();
    }

    public BoxImpl(CFacility _facility, InternalWidget _parent) {
        super(BoxFactory.getFactory(_facility), _parent, null);
        content = new WidgetImpl(_facility, this).asWidget();
        factoryKey = new StringImpl(_facility, this,
                content.getWidgetFactory().getFactoryKey());
        byteLen = factoryKey.getBufferSize() + content.getBufferSize();
    }

    @Override
    public BoxFactory getWidgetFactory() {
        return (BoxFactory) super.getWidgetFactory();
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
    public BoxImpl recreate(
            final UnmodifiableByteBufferFactory _unmodifiable) {
        return new BoxImpl(getWidgetFactory(),
                getInternalWidgetParent(), _unmodifiable.duplicateByteBuffer());
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

    public class _Box extends _Widget implements DurableBox {
        @Override
        public _Widget resolve(final String _path) {
            if (_path.length() == 0)
                return this;
            if ("factoryKey".equals(_path))
                return factoryKey.asWidget();
            if ("content".equals(_path))
                return content;
            if (_path.startsWith("content/"))
                return content.resolve(_path.substring(8));
            return null;
        }

        @Override
        public String boxedFactoryKey() {
            return factoryKey.asWidget().getValue();
        }

        @Override
        public void expectedFactoryKey(String _value) throws UnexpectedValueException {
            if (!_value.equals(factoryKey.asWidget().getValue()))
                throw new UnexpectedValueException("expected " + _value + ", not " + factoryKey.asWidget().getValue());
        }

        @Override
        public Widget getContent() {
            return content;
        }

        @Override
        public void putCopy(Widget _widget) throws InvalidWidgetContentException {
            factoryKey.asWidget().setValue(_widget.getWidgetFactory().getFactoryKey());
            int oldContentLength = content.getBufferSize();
            content.clearWidgetParent();
            content = new WidgetImpl(_widget.getWidgetFactory(),
                    BoxImpl.this, _widget.createUnmodifiable().duplicateByteBuffer()).asWidget();
            byteBuffer = null;
            int delta = content.getBufferSize() - oldContentLength;
            byteLen += delta;
            notifyParent(delta);
        }

        @Override
        public String apply(final String _params, final String _contentType,
                            final UnmodifiableByteBufferFactory _contentFactory)
                throws WidgetException {
            if ("expectedFactoryKey".equals(_params)) {
                String newValue = StringFactory.readString(_contentFactory.duplicateByteBuffer());
                asWidget().expectedFactoryKey(newValue);
                return null;
            }
            if ("putCopy".equals(_params)) {
                WidgetFactory iwf =
                        getWidgetFactory().getFacility().getWidgetFactory(_contentType);
                _Widget iw = iwf.newInternalWidget(BoxImpl.this,
                        _contentFactory.duplicateByteBuffer()).asWidget();
                factoryKey.asWidget().setValue(iwf.getFactoryKey());
                int oldContentLength = content.getBufferSize();
                content.clearWidgetParent();
                content = iw;
                byteBuffer = null;
                int delta = content.getBufferSize() - oldContentLength;
                byteLen += delta;
                notifyParent(delta);
                return null;
            }
            throw new InvalidWidgetParamsException(_params);
        }
    }
}
