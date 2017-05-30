package net.corda.core.serialization.amqp.custom

import net.corda.core.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x500.X500Name
import java.lang.reflect.Type

object ASN1Serializer : CustomSerializer.Implements<ASN1Object>(ASN1Object::class.java) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()

    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: ASN1Object, data: Data, type: Type, output: SerializationOutput) {
        output.writeObject(obj.encoded, data, clazz)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): ASN1Object {
        val binary = input.readObject(obj, schema, ByteArray::class.java) as ByteArray
        return X500Name.getInstance(ASN1InputStream(binary).readObject())
    }
}