package com.fincatto.documentofiscal.nfe400.transformers;

import com.fincatto.documentofiscal.nfe310.classes.nota.NFFormaPagamentoMoeda;
import org.simpleframework.xml.transform.Transform;

public class NFFormaPagamentoMoedaTransformer implements Transform<NFFormaPagamentoMoeda> {

    @Override
    public NFFormaPagamentoMoeda read(final String codigo) throws Exception {
        return NFFormaPagamentoMoeda.valueOfCodigo(codigo);
    }

    @Override
    public String write(final NFFormaPagamentoMoeda pagamentoMoeda) throws Exception {
        return pagamentoMoeda.getCodigo();
    }
}