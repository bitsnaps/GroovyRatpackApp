import app.model.FileService
import app.services.DefaultFileService
import com.corposense.ocr.demo.ImageConverter
import com.corposense.ocr.demo.PdfCombiner
import com.corposense.ocr.demo.SearchableImagePdf
import com.corposense.ocr.demo.ExtractImage
import com.corposense.ocr.demo.ImageLocationsAndSize
import com.corposense.ocr.demo.ImageProcessing
import com.corposense.ocr.demo.ImageText
import com.corposense.ocr.demo.PdfConverter

import com.corposense.ocr.demo.TextPdf
import com.corposense.ocr.demo.Utils

import ratpack.form.Form
import ratpack.form.UploadedFile
import ratpack.thymeleaf3.ThymeleafModule
import java.nio.file.Path

import static ratpack.thymeleaf3.Template.thymeleafTemplate
import static ratpack.groovy.Groovy.ratpack
import ratpack.server.BaseDir
import java.nio.file.Paths


String uploadDir = 'uploads'
String publicDir = 'public'
String generatedFilesDir = "generatedFiles"

Path baseDir = BaseDir.find("${publicDir}/${uploadDir}")
Path baseGeneratedFilesDir = BaseDir.find("${publicDir}/${generatedFilesDir}")
//def baseDir = BaseDir.findBaseDir()
//def baseDir = BaseDir.find(".")

Path generatedFilesPath = baseGeneratedFilesDir.resolve(generatedFilesDir)
Path uploadPath = baseDir.resolve(uploadDir)
//def uploadPath = baseDir.getRoot().resolve(uploadDir)
//def uploadPath = baseDir.getRoot().resolve("${publicDir}/${uploadDir}")

ratpack {
    serverConfig {
        props("application.properties")
        development(true)
        maxContentLength(26214400)

    }
    bindings {
        module(ThymeleafModule)
        bind(FileService, DefaultFileService)
        bind(ImageLocationsAndSize)
        bind(Utils)
        bind(SearchableImagePdf)
        bind(ImageProcessing)
        bind(ExtractImage)
        bind(ImageText)
        bind(TextPdf)
        bind(PdfConverter)
        bind(PdfCombiner)
        bind(ImageConverter)


    }
    handlers {
        prefix("upload"){
            post {
                SearchableImagePdf searchableImagePdf,
                ImageLocationsAndSize imageLocationsAndSize, ImageProcessing imageProcess,
                ExtractImage extractImage,
                ImageText imagetext,
                TextPdf textPdf,
                PdfConverter pdfConverter,
                PdfCombiner pdfCombiner,
                ImageConverter imageConverter,

                FileService fileService->

                    parse(Form.class).then({ Form form ->
                        UploadedFile f = form.file("upload")
                        String options = form.get('options')

                        String name = fileService.save(f, uploadPath.toString())
                        File filePath = new File("${uploadPath}/${name}")
                        String inputFile = filePath.toString()

                              if (fileService.isPdfFile(f)) {
                                if (options == "SearchablePDF") {
                                    pdfConverter.produceSearchablePdf(inputFile)
                                    String outputFilePath = "mergedImgPdf.pdf"
                                    File mergedFiles = new File(generatedFilesPath.toString(), "${outputFilePath}")
                                    PdfCombiner.mergePdfDocuments( inputFile,"newFile_pdf_", mergedFiles.toString())

                                    File startDir = new File(getClass().getResource("createdFiles").toURI())
                                    startDir.eachFileRecurse() {
                                        if (it.name.endsWith('.png')  || it.name.endsWith('.pdf')) {
                                            it.delete()
                                        }
                                    }
                                    redirect "/show/$outputFilePath/$name"

                                }
                                if(options == "Textoverlay"){
                                    pdfConverter.produceTextOverlay(inputFile)
                                    String outputFilePath = "mergedText.pdf"
                                    File outputFile1 = new File(generatedFilesPath.toString(), "${outputFilePath}")
                                    PdfCombiner.mergePdfDocuments(inputFile, "ocrDemo_pdf_", outputFile1.toString())

                                    File startDir = new File(getClass().getResource("createdFiles").toURI())
                                    startDir.eachFileRecurse() {
                                        if (it.name.endsWith('.png')  || it.name.endsWith('.pdf')) {
                                            it.delete()
                                        }
                                    }

                                    redirect "/show/$outputFilePath/$name"
                                }

                            }else{
                                if (options == "SearchablePDF") {

                                    String imageNBorder = imageConverter.createTextOnlyPdf(inputFile)
                                    String ExistingPdfFilePath = "textonly_pdf_1.pdf"
                                    String outputFilePath = "newFile_1.pdf"
                                    String pdfFile = imageLocationsAndSize.createPdfWithOriginalImage(ExistingPdfFilePath,
                                            outputFilePath , imageNBorder)
                                    File pdfOutPutFile = new File(pdfFile)
                                    pdfOutPutFile.renameTo(new File(generatedFilesPath.toString(),pdfOutPutFile.getName()))

                                    File startDir = new File(getClass().getResource("createdFiles").toURI())
                                    startDir.eachFileRecurse() {
                                        if (it.name.endsWith('.png')  || it.name.endsWith('.pdf')) {
                                            it.delete()
                                        }
                                    }

                                    redirect "/appear/$outputFilePath/$name"
                                }
                                 if(options == "Textoverlay"){
                                     String fulltext = imageConverter.produceText(inputFile)
                                    String outputFilePath = "ocrDemo_1.pdf"
                                    TextPdf textpdf = new TextPdf(fulltext, outputFilePath)

                                    String doc = textpdf.generateDocument(fulltext, 1)
                                     File pdfOutPutFile = new File(doc)
                                     pdfOutPutFile.renameTo(new File(generatedFilesPath.toString(), pdfOutPutFile.getName()))

                                     File startDir = new File(getClass().getResource("createdFiles").toURI())
                                     startDir.eachFileRecurse() {
                                         if (it.name.endsWith('.png')  || it.name.endsWith('.pdf')) {
                                             it.delete()
                                         }
                                     }

                                    redirect "/appear/$outputFilePath/$name"
                                }
                            }
                })
            }
            get(":outputFilePath"){
                FileService fileService ->
                    response.sendFile(fileService.get(pathTokens.outputFilePath))
            }

            get(":name"){
                FileService fileService ->
                    response.sendFile(fileService.get(pathTokens.name))
            }

        }

        get('name/:id'){
            File filePath = new File("${uploadPath}/${pathTokens['id']}")
            // you'd better check if the file exists...
            println("filePath: ${filePath}, exists: ${filePath.exists()}")
            render Paths.get(filePath.toURI())
        }

        get('file/:id'){
            File filePath = new File("${generatedFilesPath}/${pathTokens['id']}")
            // you'd better check if the file exists...
            println("filePath: ${filePath}, exists: ${filePath.exists()}")
            render Paths.get(filePath.toURI())
        }

        get("show/:outputFilePath/:name"){
            String fileId = getPathTokens().get("outputFilePath")
            String path = "/file/${fileId}"
            String fileId2 = getPathTokens().get("name")
            String path2 = "/name/${fileId2}"
            render( thymeleafTemplate("pdf", ['fullpath': path ,'fullpath2': path2]) )

        }

        get("appear/:outputFilePath/:name"){
            String fileId = getPathTokens().get("name")
            String path = "/name/${fileId}"
            String fileId2 = getPathTokens().get("outputFilePath")
            String path2 = "/file/${fileId2}"
            render( thymeleafTemplate("photo", ['fullpath': path ,'fullpath2': path2]) )
        }

        get{
            String SearchablePDF = "Create a searchable pdf with invisible text layer"
            String Textoverlay = "Just extract and show overlay"
            LinkedHashMap options = ['pdf':SearchablePDF,'text':Textoverlay ]
            render(thymeleafTemplate("index",options))
        }

    }
}

