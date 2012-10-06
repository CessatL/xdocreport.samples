package fr.opensagres.xdocreport.converter.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.activation.DataSource;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;

import fr.opensagres.xdocreport.converter.BinaryFile;
import fr.opensagres.xdocreport.converter.ConverterRegistry;
import fr.opensagres.xdocreport.converter.ConverterResource;
import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.IConverter;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.converter.Request;
import fr.opensagres.xdocreport.converter.XDocConverterException;
import fr.opensagres.xdocreport.core.document.DocumentKind;
import fr.opensagres.xdocreport.core.io.IOUtils;
import fr.opensagres.xdocreport.core.utils.HttpHeaderUtils;

/**
 * REST Web Service
 *
 * @author pleclercq
 */
@Path( "/" )
public class ConverterResourceImpl
    implements ConverterResource
{

    private static final String DOWNLOAD_OPERATION = "download";

    public BinaryFile convertPDF( Request request )
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 1) Create options ODT 2 PDF to select well converter form the
        // registry
        ConverterTypeTo to=        ConverterTypeTo.PDF;
        Options options = Options.getFrom( DocumentKind.ODT ).to( to );

        // 2) Get the converter from the registry
        IConverter converter = ConverterRegistry.getRegistry().getConverter( options );

        // 3) Convert ODT 2 PDF

        try
        {

            converter.convert( new ByteArrayInputStream( request.getContent() ), out, options );
        }
        catch ( XDocConverterException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        BinaryFile response = new BinaryFile();

        response.setContent( new ByteArrayInputStream( out.toByteArray() ) );
        response.setFileName( request.getFilename() + ".pdf" );

        return response;
    }


    public Response convert( String outputFormat, final DataSource content, String operation )
    {
        try
        {
            // 1) Get the converter type to use
            ConverterTypeTo to = ConverterTypeTo.valueOf( outputFormat );
            if ( to == null )
            {
                throw new XDocConverterException( "Converter service cannot support the output format=" + outputFormat );
            }
            // 2) retrieve the document kind from the input mimeType
            String mimeType = content.getContentType();
            DocumentKind documentKind = DocumentKind.fromMimeType( mimeType );
            if ( documentKind == null )
            {
                throw new XDocConverterException( "Converter service cannot support mime-type=" + mimeType );
            }
            // 3) Get the converter from the registry
            final Options options = Options.getFrom( documentKind ).to( to );
            final IConverter converter = ConverterRegistry.getRegistry().getConverter( options );

            // 4) Create an instance of JAX-RS StreamingOutput to convert the inputstream and set the result in the
            // response stream.
            StreamingOutput output = new StreamingOutput()
            {
                public void write( OutputStream out )
                    throws IOException, WebApplicationException
                {
                    try
                    {
                        converter.convert( content.getInputStream(), out, options );
                    }
                    catch ( XDocConverterException e )
                    {
                        throw new WebApplicationException( e );
                    }
                    finally
                    {
                        IOUtils.closeQuietly( out );
                    }

                }
            };
            // 5) Create the JAX-RS response builder.
            ResponseBuilder responseBuilder = Response.ok( output, MediaType.valueOf( to.getMimeType() ) );
            if ( isDownload( operation ) )
            {
                // The converted document must be downloaded, add teh well content-disposition header.
                String fileName = content.getName();
                responseBuilder.header( HttpHeaderUtils.CONTENT_DISPOSITION_HEADER,
                                        HttpHeaderUtils.getAttachmentFileName( getOutputFileName( fileName, to ) ) );
            }
            return responseBuilder.build();

        }
        catch ( XDocConverterException e )
        {
            throw new WebApplicationException( e );

        }
    }

    /**
     * Returns true if operation is download and false otherwise.
     *
     * @param operation
     * @return
     */
    private boolean isDownload( String operation )
    {
        return DOWNLOAD_OPERATION.equals( operation );
    }

    /**
     * Returns the output file name.
     *
     * @param filename
     * @param to
     * @return
     */
    protected String getOutputFileName( String filename, ConverterTypeTo to )
    {
        StringBuilder name = new StringBuilder( filename.replace( '.', '_' ) );
        name.append( '.' );
        name.append( to.getExtension() );
        return name.toString();
    }
}