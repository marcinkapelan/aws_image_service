import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min';
import React, { Component } from 'react';
import axios from 'axios';
import Gallery from "react-grid-gallery";
import ResizeDialog from './ResizeDialog';
import JSZip from "jszip"
import JSZipUtils from "jszip-utils"
import { saveAs } from 'file-saver';


const baseUrl = "http://localhost:8080";

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
                        let fullName = item.url.match(".amazonaws.com\/(.*)\\?X-Amz-Algorithm")[1];
                        let name = fullName.substring(fullName.indexOf("_") + 1);

                        let imageData = {
                            src: item.url,
                            thumbnail: item.url,
                            thumbnailWidth: 400,
                            thumbnailHeight: 300,
                            isSelected: false,
                            caption: name + " Size: " + Math.floor(item.size / 1024) + "KB",
                            size: item.size,
                            name: fullName
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
                    name: item.name,
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
        if (this.state.selectedFiles == null) {
            alert("No file has been chosen");
        }
        else {
            let presignedPostPromises = [];
            let postImageToS3Promises = [];
            for (let i = 0; i < this.state.selectedFiles.length; i++) {
                presignedPostPromises.push(axios.get(baseUrl + "/presignedpost", {
                    params: {
                        fileName: this.state.selectedFiles[i].name
                    }
                }));
            }
            axios.all(presignedPostPromises)
                .then(responses => {
                    responses.forEach((response) => {
                        let file;
                        for (let i = 0; i < this.state.selectedFiles.length; i++) {
                            var responseFileName = response.data.fields.key;
                            var responseShortFileName = responseFileName.substring(responseFileName.indexOf('_') + 1);
                            if (responseShortFileName === this.state.selectedFiles[i].name) {
                                file = this.state.selectedFiles[i];
                            }
                        }
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

        let imageDownloadPromises = [];

        this.state.images.forEach((image) => {
            if (image.isSelected) {
                imageDownloadPromises.push(new Promise((resolve, reject) => JSZipUtils.getBinaryContent(image.src, (err, data) => {
                    if(err) {
                        reject(err);
                    }
                    else{
                        let fullName = image.src.match(".amazonaws.com\/(.*)\\?X-Amz-Algorithm")[1];
                        zip.file(fullName, data,  {binary:true});
                        resolve();
                    }
                })));
            }
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
    };

    render() {
        return (
            <div className="container">
                <div className="row">
                    <div className="col-md-auto mt-1">
                        <input type = "file" name = "file" onChange = {this.onFilesChangeHandler} multiple/>
                    </div>
                    <div className="col-md-2 mt-1 ml-md-5">
                        <button type="button" className="btn btn-success" onClick = {this.onUploadClickHandler}>Upload</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-primary" onClick = {this.onSelectAllClickHandler}>{!this.state.selectedImages ? "Select all" : "Unselect all"}</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-primary" disabled={!this.state.selectedImages} onClick = {this.onDownloadClickHandler}>Download</button>
                    </div>
                    <div className="col-md-auto mt-1">
                        <button type="button" className="btn btn-danger" disabled={!this.state.selectedImages} onClick = {() => alert("TODO")}>Delete</button>
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