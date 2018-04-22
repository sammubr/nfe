package com.fincatto.documentofiscal.nfe310.danfe;

import com.fincatto.documentofiscal.DFAmbiente;
import com.fincatto.documentofiscal.DFModelo;
import com.fincatto.documentofiscal.nfe310.classes.nota.NFNotaProcessada;
import com.fincatto.documentofiscal.parsers.DFParser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanArrayDataSource;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

public class Danfe {

    private NFNotaProcessada notaProcessada;

    public Danfe(String xml) {
        this(new DFParser().notaProcessadaParaObjeto(xml));
    }

    public Danfe(NFNotaProcessada notaProcessada) {
        this.notaProcessada = notaProcessada;
    }

    public byte[] gerarDanfeNFe(byte[] logoEmpresa) throws Exception {
        return toPDF(createJasperPrintNFe(logoEmpresa));
    }

    public byte[] gerarDanfeNFCe(String informacoesComplementares, boolean mostrarMsgFinalizacao, NFCePagamento... pags) throws Exception {
        return toPDF(createJasperPrintNFCe(informacoesComplementares, mostrarMsgFinalizacao, pags));
    }

    private static byte[] toPDF(JasperPrint print) throws JRException {
        return JasperExportManager.exportReportToPdf(print);
    }

    private JasperPrint createJasperPrintNFe(byte[] logoEmpresa) throws IOException, JRException, ParserConfigurationException, SAXException {
        if (!DFModelo.NFE.equals(notaProcessada.getNota().getInfo().getIdentificacao().getModelo())) {
            throw new IllegalStateException("Nao e possivel gerar DANFe NFe de uma NFCe");
        }

        try (InputStream in = Danfe.class.getClassLoader().getResourceAsStream("danfe/DANFE_NFE_RETRATO.jrxml");
             InputStream subDuplicatas = Danfe.class.getClassLoader().getResourceAsStream("danfe/DANFE_NFE_DUPLICATAS.jrxml")) {

            final JRPropertiesUtil jrPropertiesUtil = JRPropertiesUtil.getInstance(DefaultJasperReportsContext.getInstance());
            jrPropertiesUtil.setProperty("net.sf.jasperreports.xpath.executer.factory", "net.sf.jasperreports.engine.util.xml.JaxenXPathExecuterFactory");


            Map<String, Object> parameters = new HashMap<>();
            JasperReport subDuplicatasComp = JasperCompileManager.compileReport(subDuplicatas);
            parameters.put("SUBREPORT_DUPLICATAS", subDuplicatasComp);
            parameters.put("LOGO_EMPRESA", (logoEmpresa == null ? null : new ByteArrayInputStream(logoEmpresa)));

            JasperReport report = JasperCompileManager.compileReport(in);
            return JasperFillManager.fillReport(report, parameters, new JRXmlDataSource(convertStringXMl2DOM(), "/nfeProc/NFe/infNFe/det"));
        }

    }

    private Document convertStringXMl2DOM() throws ParserConfigurationException, IOException, SAXException {
        try (StringReader stringReader = new StringReader(notaProcessada.toString())) {
            InputSource inputSource = new InputSource();
            inputSource.setCharacterStream(stringReader);
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputSource);
        }
    }

    private JasperPrint createJasperPrintNFCe(String informacoesComplementares, boolean mostrarMsgFinalizacao, NFCePagamento... pags) throws IOException, WriterException, JRException {
        if (!DFModelo.NFCE.equals(notaProcessada.getNota().getInfo().getIdentificacao().getModelo())) {
            throw new IllegalStateException("Nao e possivel gerar DANFe NFCe de uma NFe");
        }

        try (InputStream in = Danfe.class.getClassLoader().getResourceAsStream("danfe/DANFE_NFCE.jrxml");
             InputStream subItens = Danfe.class.getClassLoader().getResourceAsStream("danfe/DANFE_NFCE_ITENS.jrxml");
             InputStream subPagamentos = Danfe.class.getClassLoader().getResourceAsStream("danfe/DANFE_NFCE_PAGAMENTOS.jrxml")) {

            boolean homologacao = notaProcessada.getNota().getInfo().getIdentificacao().getAmbiente().equals(DFAmbiente.HOMOLOGACAO);
            List<NFCePagamento> pgtos = Arrays.asList(pags);

            Map<String, Object> parameters = new HashMap<>();
            JasperReport subPagamentosComp = JasperCompileManager.compileReport(subPagamentos);
            JasperReport subItensComp = JasperCompileManager.compileReport(subItens);
            parameters.put("SUBREL_PAGAMENTOS", subPagamentosComp);
            parameters.put("SUBREL", subItensComp);
            parameters.put("PAGAMENTOS", pgtos);
            parameters.put("QR_CODE", gerarQRCode());
            parameters.put("CHAVE_ACESSO_FORMATADA", formatarChaveAcesso());
            parameters.put("INFORMACOES_COMPLEMENTARES", informacoesComplementares);
            parameters.put("MOSTRAR_MSG_FINALIZACAO", mostrarMsgFinalizacao);
            parameters.put("URL_CONSULTA", homologacao ? notaProcessada.getNota().getInfo().getIdentificacao().getUf().getQrCodeHomologacao() : notaProcessada.getNota().getInfo().getIdentificacao().getUf().getQrCodeProducao());

            JasperReport report = JasperCompileManager.compileReport(in);
            return JasperFillManager.fillReport(report, parameters, new JRBeanArrayDataSource(new Object[] { notaProcessada }));
        }
    }

    private String formatarChaveAcesso() {
        return StringUtils.join(notaProcessada.getNota().getInfo().getChaveAcesso().split("(?<=\\G....)"), " ");
    }

    /**
     * Geracao do QRCode com ZXing
     * http://repo1.maven.org/maven2/com/google/zxing/core/3.2.0/
     */
    private BufferedImage gerarQRCode() throws WriterException {
        int size = 250;
        Map<EncodeHintType, Object> hintMap = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hintMap.put(EncodeHintType.MARGIN, 1); /* default = 4 */
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix byteMatrix = qrCodeWriter.encode(notaProcessada.getNota().getInfoSuplementar().getQrCode(), BarcodeFormat.QR_CODE, size, size, hintMap);
        int crunchifyWidth = byteMatrix.getWidth();
        BufferedImage image = new BufferedImage(crunchifyWidth, crunchifyWidth, BufferedImage.TYPE_INT_RGB);
        image.createGraphics();

        Graphics2D graphics = (Graphics2D) image.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, crunchifyWidth, crunchifyWidth);
        graphics.setColor(Color.BLACK);

        for (int i = 0; i < crunchifyWidth; i++) {
            for (int j = 0; j < crunchifyWidth; j++) {
                if (byteMatrix.get(i, j)) {
                    graphics.fillRect(i, j, 1, 1);
                }
            }
        }
        return image;
    }

    public static class NFCePagamento {

        private String formaPagamento;
        private BigDecimal valor;

        public NFCePagamento(String formaPagamento, BigDecimal valor) {
            this.formaPagamento = formaPagamento;
            this.valor = valor;
        }

        public String getFormaPagamento() {
            return formaPagamento;
        }

        public void setFormaPagamento(String formaPagamento) {
            this.formaPagamento = formaPagamento;
        }

        public BigDecimal getValor() {
            return valor;
        }

        public void setValor(BigDecimal valor) {
            this.valor = valor;
        }
    }

}
