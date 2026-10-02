import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HexFormat;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DynamicMessage;
import org.apache.avro.Schema;
import org.apache.avro.generic.*;
import org.apache.avro.io.*;

/** JDK 21; pinned dependency download commands are in topics/serialization.html. */
public class SerializationDemo {
    public static void main(String[] args) throws Exception {
        json(); xml(); protobuf(); avro();
    }

    static void json() throws Exception {
        var mapper = new ObjectMapper();
        String token = "9007199254740993";
        System.out.println("JSON as decimal = " + mapper.readValue(token, BigDecimal.class).toPlainString());
        System.out.println("JSON via double = " + BigDecimal.valueOf(mapper.readValue(token, Double.class)).toPlainString());
    }

    static void xml() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
        });
        String a = "<p:payment xmlns:p='urn:pay:v1' amountMinor='150'/>";
        String b = "<x:payment xmlns:x='urn:pay:v1' amountMinor='150'/>";
        var left = builder.parse(new InputSource(new StringReader(a))).getDocumentElement();
        var right = builder.parse(new InputSource(new StringReader(b))).getDocumentElement();
        boolean sameName = left.getNamespaceURI().equals(right.getNamespaceURI())
                && left.getLocalName().equals(right.getLocalName());
        System.out.println("XML expanded names equal = " + sameName);
        String withDtd = "<!DOCTYPE payment [<!ENTITY amount '150'>]><payment>&amount;</payment>";
        try {
            builder.parse(new InputSource(new StringReader(withDtd)));
            throw new AssertionError("DOCTYPE was accepted");
        } catch (SAXParseException expected) {
            System.out.println("XML DOCTYPE = rejected");
        }
    }

    // Dynamic descriptors avoid protoc/code generation; these are real protobuf messages.
    static Descriptor protoSchema(boolean fee, FieldDescriptorProto.Type amountType) throws Exception {
        var message = DescriptorProto.newBuilder().setName("Payment")
                .addField(field("amount_minor", 1, amountType))
                .addField(field("currency", 2, FieldDescriptorProto.Type.TYPE_STRING));
        if (fee) message.addField(field("fee_minor", 3, FieldDescriptorProto.Type.TYPE_INT64));
        var file = FileDescriptorProto.newBuilder().setName("payment.proto")
                .setSyntax("proto3").addMessageType(message).build();
        return FileDescriptor.buildFrom(file, new FileDescriptor[0]).findMessageTypeByName("Payment");
    }

    static FieldDescriptorProto field(String name, int number, FieldDescriptorProto.Type type) {
        return FieldDescriptorProto.newBuilder().setName(name).setNumber(number)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL).setType(type).build();
    }

    static void protobuf() throws Exception {
        var v2 = protoSchema(true, FieldDescriptorProto.Type.TYPE_INT64);
        var v1 = protoSchema(false, FieldDescriptorProto.Type.TYPE_INT64);
        var changed = protoSchema(false, FieldDescriptorProto.Type.TYPE_SINT64);
        byte[] bytes = DynamicMessage.newBuilder(v2)
                .setField(v2.findFieldByNumber(1), 150L)
                .setField(v2.findFieldByNumber(2), "USD")
                .setField(v2.findFieldByNumber(3), 7L).build().toByteArray();
        var old = DynamicMessage.parseFrom(v1, bytes);
        System.out.println("protobuf bytes = " + HexFormat.ofDelimiter(" ").formatHex(bytes));
        System.out.println("protobuf int64 amount = " + old.getField(v1.findFieldByNumber(1)));
        System.out.println("protobuf sint64 amount = " + DynamicMessage.parseFrom(changed, bytes)
                .getField(changed.findFieldByNumber(1)));
        var throughOld = DynamicMessage.parseFrom(v2, old.toByteArray());
        System.out.println("protobuf fee after binary relay = " + throughOld.getField(v2.findFieldByNumber(3)));
        // Copying only known fields models conversion through an old application DTO.
        var rebuilt = DynamicMessage.newBuilder(v1);
        old.getAllFields().forEach(rebuilt::setField);
        var throughDto = DynamicMessage.parseFrom(v2, rebuilt.build().toByteArray());
        System.out.println("protobuf fee after known-field copy = " + throughDto.getField(v2.findFieldByNumber(3)));
    }

    static void avro() throws Exception {
        var writer = new Schema.Parser().parse("""
            {"type":"record","name":"Payment","fields":[
              {"name":"amountMinor","type":"long"},
              {"name":"feeMinor","type":"long"}]}
            """);
        var reordered = new Schema.Parser().parse("""
            {"type":"record","name":"Payment","fields":[
              {"name":"feeMinor","type":"long"},
              {"name":"amountMinor","type":"long"}]}
            """);
        var reader = new Schema.Parser().parse("""
            {"type":"record","name":"Payment","fields":[
              {"name":"feeMinor","type":"long"},
              {"name":"amountMinor","type":"long"},
              {"name":"currency","type":"string","default":"USD"}]}
            """);
        var payment = new GenericData.Record(writer);
        payment.put("amountMinor", 150L); payment.put("feeMinor", 7L);
        var out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<GenericRecord>(writer).write(payment, encoder);
        encoder.flush();
        byte[] bytes = out.toByteArray();
        var wrong = new GenericDatumReader<GenericRecord>(reordered).read(null,
                DecoderFactory.get().binaryDecoder(bytes, null));
        var resolved = new GenericDatumReader<GenericRecord>(writer, reader).read(null,
                DecoderFactory.get().binaryDecoder(bytes, null));
        System.out.println("avro bytes = " + HexFormat.ofDelimiter(" ").formatHex(bytes));
        System.out.println("avro wrong writer schema = " + wrong);
        System.out.println("avro writer + reader = " + resolved);
    }
}
