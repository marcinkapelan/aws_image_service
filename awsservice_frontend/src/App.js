import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min';
import React, { Component } from 'react';
import axios from 'axios';
import Gallery from "react-grid-gallery";
import ResizeDialog from './ResizeDialog';
import JSZip from "jszip"
import JSZipUtils from "jszip-utils"
import { saveAs } from 'file-saver';
import { baseUrl } from "./Constants";


class App extends Component {

    constructor(props) {
        super(props);
        this.state = {
            selectedFiles: null,

            images: [],
            selectedImages: false,

            resizeDialogOpened: false
        }
        this.fetchImageUrlsFromS3 = this.fetchImageUrlsFromS3.bind(this);
    };

    componentDidMount() {
        this.fetchImageUrlsFromS3()
    }

    fetchImageUrlsFromS3() {
        this.setState({
            images: []
        });
        axios.get(baseUrl + "/presignedurls")
            .then((response) => {
                console.log(response);
                response.data.forEach((item) => {
                        let fullName = App.extractFileNameFromPresignedUrl(item.url);
                        let name = fullName.substring(fullName.indexOf("_") + 1);

                        let imageData = {
                            src: item.url,
                            thumbnail: item.url,
                            thumbnailWidth: 400,
                            thumbnailHeight: 300,
                            isSelected: false,
                            caption: name + " Size: " + Math.floor(item.size / 1024) + "KB" + " Resolution: " + item.width + " x " + item.height,
                            size: item.size,
                            fullName: fullName,
                            width: item.width,
                            height: item.height,
                        }

                        this.setState({
                            images: this.state.images.concat(imageData)
                        })
                    }
                );
            })
            .catch((error) => {
                console.log(error);
            });
    }

    onImageSelected = (index, image) => {
        var images = this.state.images.slice();
        var img = images[index];
        img.isSelected = !img.isSelected;

        this.setState({
            images: images
        }, () => {
            let num = 0;
            this.state.images.forEach((item) => {
                if (item.isSelected) {
                    num++;
                }
            });
            this.setState({
                selectedImages: num > 0
            })
        })
    };

    onSelectAllClickHandler = () => {
        var images = this.state.images.slice();

        this.setState({
            selectedImages: !this.state.selectedImages
        }, () => {
            images.forEach((item) => {
                item.isSelected = this.state.selectedImages
            });
            this.setState({
                images: images
            })
        });

    }

    onResizeClickHandler = (width, height) => {
        const MAX_FILESSIZE_PER_MESSAGE = 25000000;
        let messages = [];

        let filesSizeSum = 0;
        let message = {command: "RESIZE", width: width / 100, height: height / 100, files: []};
        this.state.images.forEach((item) => {
            if (item.isSelected) {
                if (filesSizeSum + item.size > MAX_FILESSIZE_PER_MESSAGE) {
                    messages.push(message);
                    message = {command: "RESIZE", width: width / 100, height: height / 100, files: []};
                    filesSizeSum = 0;
                }

                message.files.push({
                    name: item.fullName,
                });
                filesSizeSum += item.size;
            }
        });
        if (message.files.length > 0) {
            messages.push(message);
        }
        axios.post(baseUrl + "/queue", messages)
            .then(response => {
                alert("Request delivered successfully");
                console.log(response);
            })
            .catch(error => {
                alert("Error:" + error);
                console.log(error);
            })
    }

    resizeDialogToggle = event => {
        this.setState({
            resizeDialogOpened: !this.state.resizeDialogOpened
        })
    };

    onFilesChangeHandler = event => {
        console.log(event.target.files);
        this.setState({
            selectedFiles: event.target.files
        })
    };

    onUploadClickHandler = event => {
        const maxPixels = 20000000;

        if (this.state.selectedFiles == null) {
            alert("No file has been chosen");
        }
        else {
            let validImagesGetPromises = [];
            let presignedPostPromises = [];
            let postImageToS3Promises = [];

            Array.from(this.state.selectedFiles).forEach((file) => {
                validImagesGetPromises.push(new Promise((resolve, reject) => {
                    try {
                        let image = new Image();
                        image.addEventListener('load', () => {
                            try {
                                const width  = image.naturalWidth,
                                    height = image.naturalHeight;

                                window.URL.revokeObjectURL(image.src);
                                if (width * height <= maxPixels) {
                                    return resolve({width, height, file})
                                }
                                else {
                                    throw file.name + " has resolution " + (width * height) / 1e6 + "MP. Maximum supported: " + maxPixels / 1e6 + "MP";
                                }
                            }
                            catch (error) {
                                reject(error);
                            }
                        });
                        image.src = window.URL.createObjectURL(file)
                    }
                    catch (error) {
                        return reject(error)
                    }
                }))
            });

            Promise.all(validImagesGetPromises)
                .then((validImages) => {
                    validImages.forEach((image) => {
                        presignedPostPromises.push(axios.get(baseUrl + "/presignedpost", {
                            params: {
                                fileName: image.file.name,
                                width: image.width,
                                height: image.height,
                            }
                        }));
                    })
                    axios.all(presignedPostPromises)
                        .then(responses => {
                            responses.forEach((response) => {
                                let file;
                                validImages.forEach((validImage) => {
                                    let responseFileName = response.data.fields.key;
                                    let responseShortFileName = responseFileName.substring(responseFileName.indexOf('_') + 1);
                                    if (responseShortFileName === validImage.file.name) {
                                        file = validImage.file;
                                    }
                                });
                                this.addPostImageToS3Promise(response.data, file, postImageToS3Promises)
                            });
                            axios.all(postImageToS3Promises)
                                .then((responses) => {
                                    alert("Upload successful");
                                    this.fetchImageUrlsFromS3();
                                })
                                .catch((error) => {
                                    alert("Error uploading file:\n" + error);
                                })
                        })
                        .catch(error => {
                            alert("Error fetching presigned post data:\n" + error);
                            console.log(error);
                        });
                })
                .catch((error) => {
                    alert(error);
                });
        }
    };

    addPostImageToS3Promise(postData, file, postImageToS3Promises) {
        console.log("postData.url: " + postData.url)
        const formData = new FormData()
        Object.keys(postData.fields).forEach(key => {
            formData.append(key, postData.fields[key]);
        });
        formData.append("file", file);
        postImageToS3Promises.push(axios.post(postData.url, formData))
    }

    onDownloadClickHandler = () => {
        let zip = new JSZip();
        let name = "images.zip";

        let presignedUrlPromises = [];
        let imageDownloadPromises = [];

        /*
        Workaround for a bug (or feature) specific to Chromium based browsers related with caching CORS responses.
        Src of an image can't be used twice for consecutive HTTP and XHR request. New pre-signed url has to be used.
        More info at https://serverfault.com/a/856948
        */
        this.state.images.forEach((image) => {
            if (image.isSelected) {
                presignedUrlPromises.push(axios.get(baseUrl + "/presignedurl", {
                    params: {
                        fileName: image.fullName,
                        httpMethod: "GET"
                    }
                }));
            }
        });

        axios.all(presignedUrlPromises)
            .then((responses) => {
                responses.forEach((response) => {
                    imageDownloadPromises.push(new Promise((resolve, reject) => JSZipUtils.getBinaryContent(response.data, (err, data) => {
                        if(err) {
                            reject(err);
                        }
                        else{
                            zip.file(App.extractFileNameFromPresignedUrl(response.data), data,  {binary:true});
                            resolve();
                        }
                    })));
                });
                Promise.all(imageDownloadPromises)
                    .then(() => {
                        zip.generateAsync({type:'blob'})
                            .then((content) => {
                                saveAs(content, name);
                            });
                    })
                    .catch((error) => {
                        alert("Error downloading files:\n" + error)
                    })
            });
    };

    onDeleteClickHandler = () => {
        let deleteS3ImagesPromises = [];
        this.state.images.forEach((image) => {
            if (image.isSelected) {
                deleteS3ImagesPromises.push(axios.delete(baseUrl + "/object", {
                    params: {
                        fileName: image.fullName
                    }
                }));
            }
        });
        axios.all(deleteS3ImagesPromises)
            .then(responses => {
                alert("Delete successful");
                this.fetchImageUrlsFromS3();
            })
            .catch(error => {
                alert("Error deleting file:\n" + error);
                console.log(error);
            });

    };

    static extractFileNameFromPresignedUrl(url) {
        return decodeURI(url.match(".amazonaws.com\/(.*)\\?X-Amz-Algorithm")[1]);
    }

    render() {
        return (
            <div className="container">
                <div className="row">
                    <div className="col-md-auto mt-1">
                        <input type = "file" name = "file" onChange = {this.onFilesChangeHandler} multiple accept=".jpg,.png,.bmp,.gif"/>
                    </div>
                    <div className="col-md-2 mt-1 ml-md-5">
                        <button type="button" className="btn btn-success" onClick = {this.onUploadClickHandler}>Upload</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-primary" disabled={this.state.images.length === 0} onClick = {this.onSelectAllClickHandler}>{!this.state.selectedImages ? "Select all" : "Unselect all"}</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-primary" disabled={!this.state.selectedImages} onClick = {this.onDownloadClickHandler}>Download</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-danger" disabled={!this.state.selectedImages} onClick = {this.onDeleteClickHandler}>Delete</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-primary" disabled={!this.state.selectedImages} onClick = {() => this.setState({resizeDialogOpened: true})}>Resize</button>
                    </div>
                </div>
                <div className="mt-3 row">
                    <div className="col-md-12">
                        <Gallery images={this.state.images} onSelectImage={this.onImageSelected}/>
                    </div>
                </div>
                <ResizeDialog open = {this.state.resizeDialogOpened} toggle = {this.resizeDialogToggle} onOkClicked = {this.onResizeClickHandler}></ResizeDialog>
            </div>
        );
    }

}
export default App;