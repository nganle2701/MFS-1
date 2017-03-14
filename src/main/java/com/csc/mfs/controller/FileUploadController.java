package com.csc.mfs.controller;

import com.csc.mfs.storage.StorageFileNotFoundException;
import com.csc.mfs.storage.StorageProperties;
import com.csc.mfs.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import com.csc.mfs.model.*;
import com.csc.mfs.repository.FilesRepository;
import com.csc.mfs.service.DownloadService;
import com.csc.mfs.service.FileService;
import com.csc.mfs.service.UserService;

@Controller
public class FileUploadController {

    private final StorageService storageService;  
 
    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }
    @Autowired
    private FilesRepository fileRepo;
    @Autowired
    private FileService fileService;
    
    @Autowired
    private StorageProperties storageProp;
    @Autowired
    private DownloadService downloadService;
    @Autowired
	private UserService userService;

    @GetMapping("/upload")
    public String listUploadedFiles(Model model) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.findUserByEmail(auth.getName());
        model.addAttribute("files", storageService
                .loadAll()
                .map(path ->
                        MvcUriComponentsBuilder
                                .fromMethodName(FileUploadController.class, "serveFile", path.getFileName().toString())
                                .build().toString())
                .collect(Collectors.toList()));
    	
    	
//      model.addAttribute("files", storageService
//      .loadAll()
//      .map(path -> path.getFileName().toString()) .collect(Collectors.toList()));
              
    	
    	
//      Path x =Paths.get("s");
//     
//      model.addAttribute("files", storageService
//      .loadAll()
//      .map(path ->
//              x)
//      .collect(Collectors.toList()));
//    	List<String> x = new Vector();
//    	x.add("124");
//    	x.add("12344");
//    	model.addAttribute("files",x);
        
        return "upload";
    }
    
    /**
     * @param This function is for responding to a download request
     * @return
     */
    @GetMapping("/download/files/{idFile}")
    public ResponseEntity<Resource> serveFile(@PathVariable Integer idFile) {
    	//get current log-in user
    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 		User user = userService.findUserByEmail(auth.getName());
 		//get download information
 		Download downLoad = new Download();
 		
 		Files fileDownLoad = fileRepo.findOne(idFile);
 		
 		
 		// check if the user have not reach the maximum download per day.
 		if(downloadService.beforeDownload(user.getId(), fileDownLoad.getSize()))
 		{
 			downLoad.setIdFile(fileDownLoad);
 	 		downLoad.setIdUser(user);
 	 		downLoad.setDatedownload(new Date());
 	 		downloadService.insert(downLoad);
 	        Resource file = storageService.loadAsResource(fileDownLoad.getPath());
 	        return ResponseEntity
 	                .ok()
 	                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+file.getFilename()+"\"")
 	                .body(file);
 		}
 		
 		Resource file = null;
 		return ResponseEntity
	                .ok()
	                .body(file);
        
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
    	
        
        Files fileDB = new Files();
        Path fileDBPath = Paths.get(storageProp.getLocation());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.findUserByEmail(auth.getName());
		Double spaceAvailable = fileService.beforeUpload(user.getId(), file.getSize()/1024.0);
		CategoriesType category = new CategoriesType(1);
		redirectAttributes.addFlashAttribute("spaceAvailable",
				spaceAvailable);
		if(spaceAvailable>=0){
			 fileDB.setName(file.getOriginalFilename());
		        fileDB.setSize((double)file.getSize()/1024.0);
		        fileDB.setDateupload(new Date());
		        fileDB.setPath((file.getOriginalFilename()).toString());
		        fileDB.setUserId(user.getId());
		        fileDB.setIdType(category);
		        fileService.insertFile(fileDB);
		        storageService.store(file);
		        fileService.afterUpload(user.getId(), file.getSize()/1024.0);
		        redirectAttributes.addFlashAttribute("message",
		                "You successfully uploaded " + file.getOriginalFilename() + "!");
		} else {
			redirectAttributes.addFlashAttribute("message",
	                "Fail to upload " + file.getOriginalFilename() + "!");
		}
       
        return "redirect:/upload";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}