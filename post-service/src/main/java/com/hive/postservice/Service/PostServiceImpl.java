package com.hive.postservice.Service;

import com.hive.DTO.Notification;
import com.hive.Utility.NotificationType;
import com.hive.postservice.DTO.*;
import com.hive.postservice.Entity.Comment;
import com.hive.postservice.Entity.Like;
import com.hive.postservice.Entity.Post;
import com.hive.postservice.FeignClientConfig.UserInterface;
import com.hive.postservice.Repository.CommentDAO;
import com.hive.postservice.Repository.LikeDAO;
import com.hive.postservice.Repository.PostDAO;
import com.hive.postservice.Utility.DateFilter;
import com.hive.postservice.Utility.PostType;
import com.hive.postservice.Utility.PostTypeFilter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService{
    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);
    @Value("${FOLDER.PATH}")
    private String FOLDER_PATH;
    private final PostDAO postDAO;
    private final CommentDAO commentDAO;
    private final LikeDAO likeDAO;
    private final UserInterface userInterface;
    private final MessageQueueService mqService;


    @Override
    @Transactional
    public PostDTO createPost(MultipartFile file, PostRequestDTO postRequestDTO) {
        if ( !isValidUserId(postRequestDTO.getUserId()) ) {
            throw new RuntimeException("[createPost] Invalid user id " + postRequestDTO.getUserId());
        }
        try {
            //? Saving File to File System
            String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").format(Timestamp.from(Instant.now()));
            String filePath = FOLDER_PATH + timestamp + "-" + file.getOriginalFilename();
            file.transferTo(new File(filePath));

            //? Saving Post Details Into DBf
            Post post = Post.builder()
                    .description(postRequestDTO.getDescription())
                    .fileName(file.getOriginalFilename())
                    .fileType(file.getContentType())
                    .filePath(filePath)
                    .createdOn(Timestamp.from(Instant.now()))
                    .userId(postRequestDTO.getUserId())
                    .isBlocked(false)
                    .postType(postRequestDTO.getPostType())
                    .aspectRatio(postRequestDTO.getAspectRatio())
                    .build();
            return entityToDTO(postDAO.save(post));
        }
        catch (IOException e) {
            throw new RuntimeException("[createPost IO] " + e);
        }
    }

    @Override
    @Transactional
    public PostDTO createPost(PostRequestDTO postRequestDTO) {
        if ( !isValidUserId(postRequestDTO.getUserId()) ) {
            throw new RuntimeException("[createPost] Invalid user id " + postRequestDTO.getUserId());
        }

        Post post = Post.builder()
                .description(postRequestDTO.getDescription())
                .fileName("NO-MEDIA")
                .fileType("NO-MEDIA")
                .filePath("NO-MEDIA")
                .createdOn(Timestamp.from(Instant.now()))
                .userId(postRequestDTO.getUserId())
                .isBlocked(false)
                .postType(postRequestDTO.getPostType())
                .aspectRatio(postRequestDTO.getAspectRatio())
                .build();
        return entityToDTO(postDAO.save(post));
    }

    @Override
    public PostDTO getPost(Long postId) {
        return entityToDTO( getPostEntity(postId));
    }

    @Override
    public byte[] getPostFile(Long postId) throws IOException {
        Post post = getPostEntity(postId);
        String filePath = post.getFilePath();
        return Files.readAllBytes(new File(filePath).toPath());
    }

    @Override
    public List<PostDTO> getPostsForUser(Long userId) {
        if( !isValidUserId(userId) )
            throw new RuntimeException("[getPostsForUser] Invalid user id " + userId);
        List<Long> userIds = userInterface.getUserFriendsIds(userId).getBody();

        return List.of(); //! INCOMPLETE
    }

    @Override
    public List<PostDTO> getRandomPosts(Integer pageNumber, Integer pageSize) {
        return postDAO
                .findRandomPosts(PageRequest.of(pageNumber, pageSize))
                .stream()
                .map(this::entityToDTO)
                .toList();
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        if( postDAO.existsById(postId) ) {
            postDAO.deleteById(postId);
        }
        else {
            throw new RuntimeException("[deletePost] Post not found with id: " + postId);
        }
    }

    @Override
    public PostDTO blockPost(Long postId) {
        Post post = getPostEntity(postId);
        post.setIsBlocked(true);
        return entityToDTO(postDAO.save(post));
    }

    @Override
    public PostDTO unBlockPost(Long postId) {
        Post post = getPostEntity(postId);
        post.setIsBlocked(false);
        return entityToDTO(postDAO.save(post));
    }

    @Override
    public Long postCount() {
        return postDAO.count();
    }

    @Override
    public PaginationInfo getAllPosts(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id"));
        Page<Post> page = postDAO.findAll(pageable);
        List<PostDTO> contents = page.getContent().stream().map(this::entityToDTO).toList();

        return PaginationInfo.builder()
                .contents(contents)
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .isLast(page.isLast())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Override
    public List<PostDTO> searchPostByDescription(String searchQuery) {
        List<Post> postList = postDAO.findUsersByDescriptionContainingIgnoreCase(searchQuery);
        return postList.stream().map(this::entityToDTO).toList();
    }

    @Override
    public List<PostDTO> getUserPosts(Long userId) {
        if( !isValidUserId(userId) )
            throw new RuntimeException("[getUserPosts] Invalid user id " + userId);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdOn");
        return postDAO
                .findByUserId(userId, sort)
                .stream()
                .map(this::entityToDTO)
                .toList();
    }

    @Override
    public PostDTO updatePost(PostDTO postDTO) {
        Post post = getPostEntity(postDTO.getId());
        post.setDescription(postDTO.getDescription());
        return entityToDTO(postDAO.save(post));
    }

    @Override
    public PaginationInfo filter(PostFilter filter) {
        Integer pageNo = filter.getPageNo();
        Integer pageSize = filter.getPageSize();
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("createdOn").ascending());

        if (filter.getDateFilter() == DateFilter.ALL && filter.getPostFile() != PostTypeFilter.ALL) {
            return filterByPostTypeOnly(filter, pageable);
        }
        else if (filter.getDateFilter() != DateFilter.ALL && filter.getPostFile() == PostTypeFilter.ALL) {
            return filterByDateOnly(filter, pageable);
        }
        else if (filter.getDateFilter() != DateFilter.ALL) {
            return filterByPostTypeAndDate(filter, pageable);
        }
        else {
            return getAllPosts(pageNo, pageSize);
        }
    }

    private PaginationInfo filterByPostTypeOnly(PostFilter filter, Pageable pageable) {
        PostType postType = PostType.TEXT_ONLY;
        if (PostTypeFilter.IMAGE_BASED == filter.getPostFile()){
            postType = PostType.IMAGE;
        }
        else if (PostTypeFilter.VIDEO_BASED == filter.getPostFile()){
            postType = PostType.VIDEO;
        }

        Page<Post> page = postDAO.findByPostType(postType, pageable);
        return pageToPaginationInfo(page);
    }

    private PaginationInfo filterByDateOnly(PostFilter filter, Pageable pageable) {
        Timestamp startDate = new Timestamp(filter.getStartingDate().getTime());
        Timestamp endDate = new Timestamp(filter.getEndingDate().getTime());

        if (filter.getDateFilter() == DateFilter.TODAY || startDate.equals(endDate)) {
            Page<Post> page = postDAO.findByCreatedOn(startDate, pageable);
            return pageToPaginationInfo(page);
        } else {
            Page<Post> page = postDAO.findByCreatedOnBetween(startDate, endDate, pageable);
            return pageToPaginationInfo(page);
        }
    }

    private PaginationInfo filterByPostTypeAndDate(PostFilter filter, Pageable pageable) {
        Timestamp startDate = new Timestamp(filter.getStartingDate().getTime());
        Timestamp endDate = new Timestamp(filter.getEndingDate().getTime());
        PostType postType = PostType.TEXT_ONLY;
        if (PostTypeFilter.IMAGE_BASED == filter.getPostFile()){
            postType = PostType.IMAGE;
        }
        else if (PostTypeFilter.VIDEO_BASED == filter.getPostFile()){
            postType = PostType.VIDEO;
        }

        if (filter.getDateFilter() == DateFilter.TODAY || startDate.equals(endDate)) {
            Page<Post> page = postDAO.findByPostTypeAndCreatedOn(postType, startDate, pageable);
            return pageToPaginationInfo(page);
        } else {
            Page<Post> page = postDAO.findByPostTypeAndCreatedOnBetween(postType, startDate, endDate, pageable);
            return pageToPaginationInfo(page);
        }
    }

    //POST METHODS ENDED
    //COMMENT METHODS STARTED

    @Override
    public CommentDTO createComment(CommentRequestDTO commentRequest) {
        if (!isValidUserId(commentRequest.getUserId())) {
            throw new RuntimeException("[createComment] Invalid user id " + commentRequest.getUserId());
        }

        Optional<Post> post = postDAO.findById(commentRequest.getPostId());
        if (post.isEmpty()) {
            throw new RuntimeException("[createComment] Invalid post id " + commentRequest.getPostId());
        }

        Comment comment = Comment.builder()
                .comment(commentRequest.getComment())
                .commentedDate(Timestamp.from(Instant.now()))
                .userId(commentRequest.getUserId())
                .isBlocked(false)
                .post(post.get())
                .build();
        comment = commentDAO.save(comment);

        try {
            Notification notification = new Notification();
            notification.setSenderId(comment.getUserId());
            notification.setRecipientId(post.get().getUserId());
            notification.setNotificationType(NotificationType.COMMENT);
            notification.setPostId(post.get().getId());
            notification.setCommentId(comment.getId());
            mqService.sendMessageToTopic("notification", notification);
        } catch (TimeoutException e) {
            log.error("[createComment] Kafka TimeoutException for comment id " + comment.getId(), e);
            throw new RuntimeException("[createComment] Kafka service timeout. Please try again later.", e);
        } catch (AuthenticationException | AuthorizationException e) {
            log.error("[createComment] Kafka authentication/authorization error for comment id " + comment.getId(), e);
            throw new RuntimeException("[createComment] Kafka authentication/authorization failed.", e);
        } catch (KafkaException e) {
            log.error("[createComment] General KafkaException for comment id " + comment.getId(), e);
            throw new RuntimeException("[createComment] Kafka service error. Please try again later.", e);
        } catch (Exception e) {
            log.error("[createComment] Error sending notification for comment id " + comment.getId(), e);
            throw new RuntimeException("[createComment] Error sending notification", e);
        }

        return entityToDTO(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        if( commentDAO.existsById(commentId) ) {
            commentDAO.deleteById(commentId);
        }
        else {
            throw new RuntimeException("[deleteComment] Comment not found with id: " + commentId);
        }
    }

    @Override
    public List<CommentDTO> getCommentsForPost(Long postId) {
        Post post = getPostEntity(postId);
        Sort sort = Sort.by(Sort.Direction.DESC, "commentedDate");
        return commentDAO
                .findByPost(post, sort)
                .stream()
                .map(this::entityToDTO)
                .toList();
    }

    @Override
    public CommentDTO getComment(Long commentId) {
        return commentDAO
                .findById(commentId)
                .map(this::entityToDTO)
                .orElseThrow(() -> new RuntimeException("[getComment] Comment not found with id: " + commentId));
    }

    @Override
    public Long commentCount(Long postId) {
        return commentDAO.countAllByPost( getPostEntity(postId));
    }

    @Override
    public CommentDTO blockComment(Long commentId) {
        Optional<Comment> commentOp = commentDAO.findById(commentId);
        if ( commentOp.isPresent() ) {
            Comment comment = commentOp.get();
            comment.setIsBlocked(true);
            return entityToDTO(commentDAO.save(comment));
        }
        return null;
    }

    @Override
    public CommentDTO unBlockComment(Long commentId) {
        Optional<Comment> commentOp = commentDAO.findById(commentId);
        if ( commentOp.isPresent() ) {
            Comment comment = commentOp.get();
            comment.setIsBlocked(false);
            return entityToDTO(commentDAO.save(comment));
        }
        return null;
    }

    //COMMENT METHODS ENDED
    //LIKE METHODS STARTED

    @Override
//    @Transactional
    public LikeDTO createLike(LikeRequestDTO likeRequest) {
        if ( !isValidUserId(likeRequest.getUserId()) )
            throw new RuntimeException("[createLike] Invalid user id " + likeRequest.getUserId());

        Optional<Post> post = postDAO.findById(likeRequest.getPostId());
        if ( post.isEmpty() )
            throw new RuntimeException("[createLike] Invalid post id " + likeRequest.getPostId());

        if ( likeDAO.existsByPostAndUserId(post.get(), likeRequest.getUserId()) )
            return null;

        Like like = Like.builder()
                .userId(likeRequest.getUserId())
                .likedDate(Timestamp.from(Instant.now()))
                .post(post.get())
                .build();
        like = likeDAO.save(like);

        Notification notification = new Notification();
        notification.setSenderId( like.getUserId());
        notification.setRecipientId( post.get().getUserId() );
        notification.setNotificationType( NotificationType.LIKE );
        notification.setPostId( post.get().getId() );

        mqService.sendMessageToTopic("notification", notification);
        return entityToDTO(like);
    }

    @Override
    public LikeDTO getLike(Long likeId) {
        return likeDAO
                .findById(likeId)
                .map(this::entityToDTO)
                .orElseThrow(() -> new RuntimeException("[getLike] Invalid likeId: " + likeId));
    }

    @Override
    public List<LikeDTO> getLikesForPost(Long postId) {
        return likeDAO
                .findByPost( getPostEntity(postId))
                .stream()
                .map(this::entityToDTO)
                .toList();
    }

    @Override
    @Transactional
    public void deleteLike(LikeRequestDTO like) {
        Post post = getPostEntity(like.getPostId());
        Optional<Like> likeOptional = likeDAO.findByPostAndUserId(post, like.getUserId());
        if (likeOptional.isPresent()) {
            Like entity = likeOptional.get();
            likeDAO.delete(entity);
        }
        else {
            throw new RuntimeException("[deleteLike] Like not found with : " + like);
        }
    }

    @Override
    public Long likeCount(Long postId) {
        return likeDAO.countByPost( getPostEntity(postId));
    }

    @Override
    public Boolean isUserLiked(LikeRequestDTO likeRequest) {
        if ( !isValidUserId(likeRequest.getUserId()) )
            throw new RuntimeException("[createLike] Invalid user id " + likeRequest.getUserId());

        Optional<Post> post = postDAO.findById(likeRequest.getPostId());
        if ( post.isEmpty() )
            throw new RuntimeException("[createLike] Invalid post id " + likeRequest.getPostId());

        return likeDAO.existsByPostAndUserId(post.get(), likeRequest.getUserId());
    }

    //LIKE METHODS ENDED
    //HELPER METHODS STARTED

    public Post getPostEntity(Long postId) {
        return postDAO.findById(postId)
                .orElseThrow(() -> new RuntimeException("[getPostEntity] Post not found with id: " + postId));
    }

    private Boolean isValidUserId(Long userId) {
        return userInterface.isUserExists(userId).getBody();
    }

    private PostDTO entityToDTO(Post post) {
        return PostDTO.builder()
                .id(post.getId())
                .description(post.getDescription())
                .fileName(post.getFileName())
                .fileType(post.getFileType())
                .filePath(post.getFilePath())
                .createdOn(post.getCreatedOn())
                .userId(post.getUserId())
                .isBlocked(post.getIsBlocked())
                .postType(post.getPostType())
                .aspectRatio(post.getAspectRatio())
                .build();
    }

    private Post dtoToEntity(PostDTO dto) {
        return Post.builder()
                .id(dto.getId())
                .description(dto.getDescription())
                .fileName(dto.getFileName())
                .fileType(dto.getFileType())
                .filePath(dto.getFilePath())
                .createdOn(dto.getCreatedOn())
                .userId(dto.getUserId())
                .isBlocked(dto.getIsBlocked())
                .postType(dto.getPostType())
                .aspectRatio(dto.getAspectRatio())
                .build();
    }

    private CommentDTO entityToDTO(Comment comment) {
        return CommentDTO.builder()
                .id(comment.getId())
                .comment(comment.getComment())
                .commentedDate(comment.getCommentedDate())
                .userId(comment.getUserId())
                .isBlocked(comment.getIsBlocked())
                .postId(comment.getPost().getId())
                .build();
    }

    private Comment dtoToEntity(CommentDTO dto) {
        return Comment.builder()
                .id(dto.getId())
                .comment(dto.getComment())
                .commentedDate(dto.getCommentedDate())
                .userId(dto.getUserId())
                .isBlocked(dto.getIsBlocked())
                .post( getPostEntity(dto.getPostId()))
                .build();
    }

    private LikeDTO entityToDTO(Like like) {
        return LikeDTO.builder()
                .id(like.getId())
                .userId(like.getUserId())
                .likedDate(like.getLikedDate())
                .postId(like.getPost().getId())
                .build();
    }

    private Like dtoToEntity(LikeDTO dto) {
        return Like.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .likedDate(dto.getLikedDate())
                .post( getPostEntity(dto.getPostId()))
                .build();
    }

    private PaginationInfo pageToPaginationInfo(Page<Post> page) {
        List<PostDTO> contents = page.getContent().stream().map(this::entityToDTO).toList();
        return PaginationInfo.builder()
                .contents(contents)
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .isLast(page.isLast())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}